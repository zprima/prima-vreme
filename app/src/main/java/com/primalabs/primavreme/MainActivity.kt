package com.primalabs.primavreme

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.google.accompanist.coil.CoilImage
import com.google.accompanist.coil.LocalImageLoader
import com.primalabs.primavreme.ui.theme.PrimaVremeTheme
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

const val LOG_TAG = "vremetag"
const val ARSO_WEATHER_IMAGE_URL = "https://vreme.arso.gov.si/app/common/images/svg/weather/"

// Helpful logging to see http body content
//internal val baseOkHttpClient: OkHttpClient = OkHttpClient
//    .Builder()
//    .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
//    .build()

val moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()

var retrofit = Retrofit.Builder()
    .baseUrl("https://vreme.arso.gov.si/api/1.0/")
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()

interface ArsoService {
    @Headers("content-type: application/json")
    @GET("locations/")
    suspend fun findLocation(@Query("loc") loc: String): Response<ArsoLocationsResult>

    @Headers("content-type: application/json")
    @GET("location/")
    suspend fun getLocationWeather(
        @Query("lang") lang: String = "sl",
        @Query("location") location: String
    ): Response<ArsoLocationResult>

}

var arsoService = retrofit.create(ArsoService::class.java)

data class ArsoLocationsResult(
    @Json(name = "features") val features: List<ArsoLocationFeature>
)

data class ArsoLocationFeature(
    @Json(name = "geometry") val geometry: ArsoLocationGeometry,
    @Json(name = "properties") val properties: ArsoLocationFeatureProperty
)

data class ArsoLocationGeometry(
    @Json(name = "coordinates") val coordinates: List<Float>
)

data class ArsoLocationFeatureProperty(
    @Json(name = "country") val country: String,
    @Json(name = "title") val title: String,
    @Json(name = "id") val id: String,
    @Json(name = "days") val days: List<ArsoLocationFeaturePropertyDay>
)

data class ArsoLocationFeaturePropertyDay(
    @Json(name = "date") val date: String,
    @Json(name = "timeline") val timeline: List<ArsoLocationFeaturePropertyDayTimeline>
)

data class ArsoLocationFeaturePropertyDayTimeline(
    @Json(name = "t") val t: String?,
    @Json(name = "tnsyn") val tMin: String?,
    @Json(name = "txsyn") val tMax: String?,
    @Json(name = "clouds_icon_wwsyn_icon") val iconName: String,
    @Json(name = "clouds_shortText") val description: String,
    @Json(name = "ff_shortText") val windDescription: String,
    @Json(name = "ff_val") val windSpeed: String,
    @Json(name = "rh_shortText") val humidityDescription: String,
    @Json(name = "rh") val humidityPercent: String,
    @Json(name = "valid") val validForDateTime: String
)

data class ArsoLocationResult(
    @Json(name="observation") val observation: ArsoForecast,
    @Json(name="forecast1h") val forecast1h: ArsoForecast,
    @Json(name="forecast24h") val forecast24h: ArsoForecast
)

data class ArsoForecast(
    @Json(name="features") val features: List<ArsoLocationFeature>
)

const val STATE_LOCATION_SELECTED_NAME = "location.state.selected.name"

class ArsoWeatherRepository(){
    suspend fun callTriggerSearch(searchInput: String): Response<ArsoLocationsResult>{
        return withContext(Dispatchers.IO){
            arsoService.findLocation(searchInput)
        }
    }

    suspend fun callLocationForecast(location: String): Response<ArsoLocationResult>{
        return withContext(Dispatchers.IO){
            arsoService.getLocationWeather(location = location)
        }
    }
}

class WeatherViewModel(private val savedStateHandle: SavedStateHandle): ViewModel(){
    val arsoLocations = MutableLiveData<List<ArsoLocationFeature>>(emptyList())
    val selectedLocationWeather = MutableLiveData<ArsoLocationResult>(null)
    val repository = ArsoWeatherRepository()

    suspend fun triggerSearch(searchInput: String){
        if(searchInput.length >= 2) {
            viewModelScope.launch() {
                val result = repository.callTriggerSearch(searchInput)
                arsoLocations.value = result.body()?.features
            }
        } else {
            arsoLocations.value = listOf()
        }
    }

    suspend fun onSearchLocationClicked(location: String){
        savedStateHandle.set(STATE_LOCATION_SELECTED_NAME, location)

        viewModelScope.launch(){
            val result = repository.callLocationForecast(location)
            selectedLocationWeather.value = result.body()
            arsoLocations.value = listOf()
        }
    }

    private fun triggerLocationWeatherFetch(location: String){
        viewModelScope.launch {
            onSearchLocationClicked(location = location)
        }
    }

    init {
        savedStateHandle.get<String>(STATE_LOCATION_SELECTED_NAME)?.let {
            Log.d(LOG_TAG, it)
            triggerLocationWeatherFetch(it)
        }

        triggerLocationWeatherFetch("Celje")
    }
}


@ExperimentalAnimationApi
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PrimaVremeTheme(false) {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    App()
                }
            }
        }
    }
}

@ExperimentalAnimationApi
@Composable
fun App(){
    val vm: WeatherViewModel = viewModel()
    val extendSearch = remember {
        mutableStateOf(false)
    }

    val imageLoader = ImageLoader.Builder(LocalContext.current)
        .componentRegistry {
            add(SvgDecoder(LocalContext.current))
        }
        .build()

    CompositionLocalProvider(LocalImageLoader provides imageLoader) {
        Scaffold(
            modifier = Modifier,

            content = {
                WeatherAppContent(vm)
            },
            topBar = {
                TopAppBar(
                    title = {
                            AnimatedVisibility(
                                visible = extendSearch.value,
                                enter =  slideInHorizontally(initialOffsetX = { it * 2 }),
                                exit = slideOutHorizontally(targetOffsetX = { it * 2 })
                            ) {
                                Text("Search", modifier = Modifier.fillMaxWidth())
                            }
                    },
                    elevation = 0.dp,
                    navigationIcon = {
                        IconButton(onClick = { /*TODO*/ }) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { extendSearch.value = !extendSearch.value }) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null)
                        }
                    }
                )
            }
        )
    }
}

@Composable
private fun WeatherAppContent(vm: WeatherViewModel) {
    val locationForecastData = vm.selectedLocationWeather.observeAsState(null)

    if(locationForecastData.value == null){
        Text("Loading...")
    } else {
        val forecastValue = locationForecastData.value!!

        Column(
            Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Column {
                CurrentForecast(forecastData = forecastValue.observation)

                Spacer(Modifier.height(16.dp))

                Forecast1h(forecast1hData = forecastValue.forecast1h)

                Spacer(Modifier.height(24.dp))

                Forecast24h(forecast24hData = forecastValue.forecast24h)
            }
        }
    }
}

@Composable
private fun CurrentForecast(forecastData: ArsoForecast){
    val properties = forecastData.features.first().properties
    val todaysForecast = properties.days.first()
    val todaysForecastDate = LocalDate.parse(todaysForecast.date, DateTimeFormatter.ISO_DATE)
    val todaysTimeline = todaysForecast.timeline.first()
    val forecastImgUrl = "${ARSO_WEATHER_IMAGE_URL}${todaysTimeline.iconName}.svg"

    CurrentForecastMain(
        locationName = properties.title,
        forecastDate = todaysForecastDate,
        forecastImgUrl = forecastImgUrl,
        temparature = todaysTimeline.t ?: "",
        description = todaysTimeline.description
    )

    CurrentForecastDetails(
        windText = todaysTimeline.windDescription,
        windValue = "${todaysTimeline.windSpeed} km/h",
        humidityText = todaysTimeline.humidityDescription,
        humidityValue = "${todaysTimeline.humidityPercent} %"
    )
}

@Composable
private fun CurrentForecastMain(locationName: String, forecastDate: LocalDate, forecastImgUrl: String, temparature: String, description: String) {
    val formatter = DateTimeFormatter.ofPattern("eeee, d. L y")
    val formattedForecastDate = forecastDate.format(formatter)

    Text(
        text = locationName.toUpperCase(Locale.ROOT),
        style = MaterialTheme.typography.h2,
        color = MaterialTheme.colors.onPrimary,
        modifier = Modifier.paddingFromBaseline(bottom = 16.dp)
    )

    Text(
        text = formattedForecastDate.capitalize(),
        style = MaterialTheme.typography.subtitle2,
        color = MaterialTheme.colors.onSecondary
    )

    Spacer(Modifier.height(16.dp))

    Row{
        CoilImage(
            data = forecastImgUrl,
            contentDescription = null,
            error = {
                Text(it.throwable.message!!)
            },
            modifier = Modifier
                .height(150.dp)
                .fillMaxWidth(.5f),
            contentScale = ContentScale.Crop,
            shouldRefetchOnSizeChange = { _, _ -> false }
        )

        Column {
            Text(
                buildAnnotatedString {
                    append(temparature)
                    withStyle(
                        SpanStyle(
                            baselineShift = BaselineShift.Superscript,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Normal
                        )
                    ) {
                        append("°C")
                    }
                },
                style = MaterialTheme.typography.h1
            )

            Text(
                text = description,
                color = MaterialTheme.colors.onPrimary
            )
        }
    }
}

@Composable
fun CurrentForecastDetails(windText: String, windValue: String, humidityText: String, humidityValue: String){
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ){
        CurrentForecastDetailItem(
            detailText = windText,
            detailValue = windValue,
            icon = Icons.Default.Air,
            iconBackgroundColor = Color(0xFFe4f4e5),
            iconColor = Color(0xFF98d2a5))

        CurrentForecastDetailItem(
            detailText = humidityText,
            detailValue = humidityValue,Icons.Default.Bloodtype,
            iconBackgroundColor = Color(0xFFe0eff6),
            iconColor = Color(0xFF7dc1e5)
        )
    }
}

@Composable
private fun CurrentForecastDetailItem(detailText: String, detailValue: String, icon: ImageVector, iconBackgroundColor: Color, iconColor: Color) {
    Row(modifier = Modifier.padding(8.dp)){
        Box(modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .size(40.dp)
            .background(color = iconBackgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor)
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column() {
            Text(detailText, fontSize = 14.sp)
            Text(detailValue, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Forecast1h(forecast1hData: ArsoForecast){
    val features = forecast1hData.features
    val properties = features.first().properties
    val todaysForecast = properties.days.first()

    Text("Danes", modifier = Modifier.padding(bottom = 8.dp))

    Row(modifier =
        Modifier.horizontalScroll(rememberScrollState())
    ){
        todaysForecast.timeline.forEach {
            Forecast1hCard(it)
        }

    }
}

@Composable
private fun Forecast1hCard(forecastData: ArsoLocationFeaturePropertyDayTimeline) {
    val forecastDate = LocalDateTime.parse(forecastData.validForDateTime, DateTimeFormatter.ISO_DATE_TIME)
    val forecastImgUrl = "${ARSO_WEATHER_IMAGE_URL}${forecastData.iconName}.svg"

    Forecast1hCardItem(
        forecastDate = forecastDate,
        forecastImgUrl = forecastImgUrl,
        temparature = forecastData.t ?: "",
        windSpeed = "${forecastData.windSpeed} km/h",
        humidityPercent = "${forecastData.humidityPercent} %",
        active = forecastDate.hour == LocalDateTime.now().hour
    )
}

@Composable
private fun Forecast1hCardItem(forecastDate: LocalDateTime, forecastImgUrl: String, temparature: String, windSpeed: String, humidityPercent: String, active: Boolean = false){
    val roundShape = RoundedCornerShape(10)
    val hourFormatter = DateTimeFormatter.ofPattern("HH:mm")

    var modifier = Modifier
        .clip(roundShape)
        .border(BorderStroke(1.dp, Color(0xFFe8e9ec)), roundShape)

    if(active){
        modifier = modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xffa5c6fd),
                    Color(0xff5a95fb)
                )
            )
        )
    }

    val textColor = if(active) MaterialTheme.colors.primary else MaterialTheme.colors.onSecondary

    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = forecastDate.format(hourFormatter),
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
        )

        CoilImage(
            data = forecastImgUrl,
            contentDescription = null,
            error = {
                Text(it.throwable.message!!)
            },
            modifier = Modifier.size(50.dp)
        )

        Text(buildAnnotatedString {
            append(temparature)
            withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 10.sp, fontWeight = FontWeight.Normal)){
                append("c")
            }
        }, fontWeight = FontWeight.Bold, color = textColor)

        Text(windSpeed, fontSize = 12.sp, color = textColor)
        Text(humidityPercent, fontSize = 12.sp, color = textColor)
    }
    Spacer(modifier = Modifier.width(16.dp))
}

@Composable
private fun Forecast24h(forecast24hData: ArsoForecast){
    val features = forecast24hData.features
    val properties = features.first().properties

    Text("Naslednjih 10 dni", modifier = Modifier.padding(bottom = 8.dp))

    Column(){
        properties.days.drop(1).forEach {
            Forecast24Card(it)
        }
    }
}

@Composable
private fun Forecast24Card(forecastDayData: ArsoLocationFeaturePropertyDay) {
    val dayNameFormatter = DateTimeFormatter.ofPattern("eeee")
    val dateFormatter = DateTimeFormatter.ofPattern("d.MM.yyyy")

    val date = LocalDate.parse(forecastDayData.date)
    val timeline = forecastDayData.timeline.first()

    val maxTemparature = timeline.tMax ?: ""
    val minTemparature = timeline.tMin ?: ""
    val description = timeline.description

    val forecastImgUrl = "${ARSO_WEATHER_IMAGE_URL}${timeline.iconName}.svg"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(Modifier.width(100.dp)) {
            Text(
                text = date.format(dayNameFormatter).toUpperCase(Locale.ROOT),
                fontSize = 14.sp
            )
            Text(
                text = date.format(dateFormatter),
                fontSize = 12.sp,
                color = MaterialTheme.colors.onSecondary
            )
        }

        Text(
            text =
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 20.sp)){
                        append(maxTemparature)
                    }
                    append(" / ")
                    append(minTemparature)
                    withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 12.sp)){
                        append(" c")
                    }
                },
            fontSize = 16.sp,
            modifier = Modifier.width(70.dp)
        )

        Text(
            text = description,
            fontSize = 12.sp,
            color = MaterialTheme.colors.onSecondary,
            modifier = Modifier.width(100.dp)
        )

        CoilImage(
            data = forecastImgUrl,
            contentDescription = null,
            error = {
                Text(it.throwable.message!!)
            },
            modifier = Modifier.size(30.dp)
        )
    }
}

//
//@Composable
//fun SearchPart(vm: WeatherViewModel) {
//    val scope = rememberCoroutineScope()
//    var searchInput by remember { mutableStateOf("") }
//    val locations by vm.arsoLocations.observeAsState()
//
//    fun triggerSearch() {
//        scope.launch() {
//            vm.triggerSearch(searchInput)
//        }
//    }
//
//    fun triggerLocationSearch(locationName: String){
//        scope.launch {
//            vm.onSearchLocationClicked(locationName)
//        }
//    }
//
//    Row(Modifier.fillMaxWidth()) {
//        OutlinedTextField(
//            value = searchInput,
//            onValueChange = { searchInput = it; triggerSearch(); },
//            modifier = Modifier.fillMaxWidth(),
//            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
//            singleLine = true
//        )
//
//        DropdownMenu(
//            expanded = locations?.size ?: 0 > 0,
//            onDismissRequest = { },
//            properties = PopupProperties(focusable = false),
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(240.dp)
//        ) {
//            locations?.forEach {
//                val locationTemp: String = it.properties.days.first().timeline.first().t!!
//                val locationName: String = it.properties.title
//
//                DropdownMenuItem(onClick = { triggerLocationSearch(locationName) }) {
//                    Row() {
//                        Text(locationTemp, modifier = Modifier.width(20.dp))
//                        Spacer(Modifier.width(16.dp))
//                        Text(locationName)
//                    }
//                }
//            }
//        }
//    }
//}
//
//@Composable
//fun DetailsPart(vm: WeatherViewModel){
//    val locationForecast by vm.selectedLocationWeather.observeAsState()
//
//    if(locationForecast != null){
//        LocationWeatherDetailsPart(locationForecast!!)
//    }
//
//    LocationWeatherDetailsPart(null)
//
//}
//
//@Composable
//fun LocationWeatherDetailsPart(locationForecast: ArsoLocationResult?){
////    val locationName: String = locationForecast.observation.features.first().properties.title
////    val currentTemp: String = locationForecast.observation.features.first().properties.days.first().timeline.first().t!!
//
//    val locationName = "CELJE"
//    val currentTemp = "12"
//    val imgUrl = "${ARSO_WEATHER_IMAGE_URL}prevCloudy_day.svg"
//
//    Column(modifier = Modifier){
//        Text(locationName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF384555))
//        Text("Sobota, 10. April, 2021", style = MaterialTheme.typography.subtitle1)
//
//        Spacer(Modifier.height(32.dp))
//
//        Row(
//            horizontalArrangement = Arrangement.Center,
//            verticalAlignment = Alignment.CenterVertically,
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(150.dp)
//        ){
//
//            CoilImage(
//                data = imgUrl,
//                contentDescription = null,
//                error = {
//                    Text(it.throwable.message!!)
//                },
//                modifier = Modifier
//                    .fillMaxHeight()
//                    .fillMaxWidth(.5f)
//                    .offset(x = 10.dp),
//                alignment = Alignment.CenterEnd
//            )
//
//            Column(
//                modifier = Modifier
//                    .fillMaxHeight()
//                    .fillMaxWidth(),
//                verticalArrangement = Arrangement.Top
//            ){
//                Spacer(Modifier.height(16.dp))
//
//                Text(text = buildAnnotatedString {
//                    append(currentTemp)
//
//                    withStyle(style = SpanStyle(
//                        fontSize = 12.sp,
//                        fontWeight = FontWeight.Light,
//                        baselineShift = BaselineShift.Superscript,
//                        color = Color(0xFFadb4bc)
//                    )){
//                        append("C")
//                    }
//                }, style = MaterialTheme.typography.h1)
//                Text("Delno Oblačno")
//            }
//        }
//
//        Spacer(Modifier.height(8.dp))
//
//        Row(
//            horizontalArrangement = Arrangement.SpaceEvenly,
//            modifier = Modifier.fillMaxWidth()
//        ){
//            Column(
//                horizontalAlignment = Alignment.CenterHorizontally,
//                modifier = Modifier.width(100.dp)
//            ){
//                Box(modifier = Modifier
//                    .size(50.dp)
//                    .background(color = Color(0xFFe4f4e7)),
//                    contentAlignment = Alignment.Center
//                ){
//                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF48725f))
//                }
//                Text("9km/h")
//            }
//
//            Column(
//                horizontalAlignment = Alignment.CenterHorizontally,
//                modifier = Modifier.width(100.dp)
//            ){
//                Box(modifier = Modifier
//                    .size(50.dp)
//                    .background(color = Color(0xFFe1f0f7)),contentAlignment = Alignment.Center){
//                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF5cabe9))
//                }
//                Text("43%")
//            }
//
//            Column(
//                horizontalAlignment = Alignment.CenterHorizontally,
//                modifier = Modifier.width(100.dp)
//            ){
//                Box(modifier = Modifier
//                    .size(50.dp)
//                    .background(color = Color(0xFFf2e1e7)), contentAlignment = Alignment.Center){
//                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFFd16083))
//                }
//                Text("5%")
//            }
//        }
//
//        Spacer(Modifier.height(32.dp))
//
//        Text("Urni pregled")
//
//        Spacer(Modifier.height(8.dp))
//
//        Row(modifier =
//        Modifier.horizontalScroll(rememberScrollState())
//        ){
//            Forecast12Card(imgUrl)
//            Forecast12Card(imgUrl)
//            Forecast12Card(imgUrl)
//            Forecast12Card(imgUrl)
//            Forecast12Card(imgUrl)
//            Forecast12Card(imgUrl)
//            Forecast12Card(imgUrl)
//            Forecast12Card(imgUrl)
//            Forecast12Card(imgUrl)
//        }
//
//        Spacer(Modifier.height(16.dp))
//
//        Text("Tedenski pregled")
//
//        Spacer(Modifier.height(8.dp))
//
//        Column(){
//            Forecast24Card()
//            Forecast24Card()
//            Forecast24Card()
//            Forecast24Card()
//            Forecast24Card()
//            Forecast24Card()
//            Forecast24Card()
//            Forecast24Card()
//            Forecast24Card()
//            Forecast24Card()
//            Forecast24Card()
//            Forecast24Card()
//            Forecast24Card()
//            Forecast24Card()
//        }
//    }
//}
//
//@Composable
//private fun Forecast12Card(imgUrl: String) {
//    Column(
//        modifier = Modifier
//            .clip(RoundedCornerShape(20f))
//            .background(
//                brush = Brush.verticalGradient(
//                    colors = listOf(
//                        Color(0xffa6c7fc),
//                        Color(0xff5c97fd)
//                    )
//                )
//            )
//            .padding(8.dp),
//        horizontalAlignment = Alignment.CenterHorizontally
//    ) {
//        Text("11:00", color = Color.White, fontSize = 14.sp)
//
//        CoilImage(
//            data = imgUrl,
//            contentDescription = null,
//            error = {
//                Text(it.throwable.message!!)
//            },
//            modifier = Modifier.size(50.dp)
//        )
//
//        Text("20c", fontWeight = FontWeight.Bold, color = Color.White)
//    }
//
//    Spacer(modifier = Modifier.width(16.dp))
//}





