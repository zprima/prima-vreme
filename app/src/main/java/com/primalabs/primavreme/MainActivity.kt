package com.primalabs.primavreme

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.primalabs.primavreme.ui.theme.PrimaVremeTheme
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

const val LOG_TAG = "vreme"

// Helpful logging to see http body content
internal val baseOkHttpClient: OkHttpClient = OkHttpClient
    .Builder()
    .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
    .build()

val moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()

var retrofit = Retrofit.Builder()
    .baseUrl("https://vreme.arso.gov.si/api/1.0/")
    .client(baseOkHttpClient)
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()

interface ArsoService {
    @Headers("content-type: application/json")
    @GET("locations/")
    suspend fun findLocation(@Query("loc") loc: String): Response<ArsoLocationResult>
}

var arsoService = retrofit.create(ArsoService::class.java)

data class ArsoLocationResult(
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
    @Json(name = "t") val t: String
)

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PrimaVremeTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    App()
                }
            }
        }
    }
}

@Composable
fun App(){


    Column(Modifier.padding(16.dp)){
        SearchPart()
        Spacer(Modifier.height(16.dp))
    }

//    val scope = rememberCoroutineScope()
//    val stuff = remember{ mutableStateOf<ArsoLocationResult?>(null) }
//
//    if(stuff.value!= null){
//        val response = stuff.value!!
//
//        LazyColumn(Modifier.padding(16.dp)) {
//            items(response.features){
//                Text(it.properties.title)
//            }
//        }
//    }
//
//    SideEffect {
//        scope.launch(Dispatchers.IO) {
//            val response = arsoService.findLocation("po")
//            stuff.value = response.body()!!
//
//            Log.d(LOG_TAG, "${stuff.value!!.features}")
//        }
//    }
}

@Composable
fun SearchPart(){
    val scope = rememberCoroutineScope()
    val arsoResponse = remember { mutableStateOf<ArsoLocationResult?>(null) }
    var x by remember{ mutableStateOf("")}
    val showDropdown = remember { mutableStateOf(false)}

    fun triggerSearch(){
        if(x.length > 2) {
            scope.launch(Dispatchers.IO) {
                arsoResponse.value = arsoService.findLocation(x).body()
                showDropdown.value = true
            }
        } else {
            showDropdown.value = false
        }
    }


    Row(Modifier.fillMaxWidth()){
        OutlinedTextField(
            value = x,
            onValueChange = { x = it; triggerSearch(); },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null)},
            singleLine = true
        )

        DropdownMenu(
            expanded = showDropdown.value,
            onDismissRequest = { showDropdown.value = false },
            properties = PopupProperties(focusable = false),
            modifier = Modifier.fillMaxWidth()
        ) {
            val locations = arsoResponse.value!!.features
            locations.forEach {
                DropdownMenuItem(onClick = { /*TODO*/ }) {
                    Row(){
                        Text(it.properties.days.first().timeline.first().t)
                        Spacer(Modifier.width(16.dp))
                        Text(it.properties.title)
                    }

                }
            }
        }
    }

}



