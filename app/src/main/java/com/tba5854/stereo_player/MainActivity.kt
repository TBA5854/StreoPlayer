package com.tba5854.stereo_player

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConnectedTv
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.media3.exoplayer.ExoPlayer
//import com.tba5854.stereo_player.core.network.WSC
//import com.tba5854.stereo_player.core.network.WSS
import com.tba5854.stereo_player.core.player.ExoPlayerManager
import com.tba5854.stereo_player.ui.theme.StreoPlayerTheme
import com.tba5854.stereo_player.domain.model.UserProfile
import com.tba5854.stereo_player.settings.Settings
import dagger.hilt.android.AndroidEntryPoint
import java.net.URI
import com.tba5854.stereo_player.ui.library.MusicImportScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)
        enableEdgeToEdge()
        setContent {
            StreoPlayerTheme {
                temp()
            }
        }
    }
}


@Composable
fun temp(){
    // android.util.Log.d("TWSt",Settings.config.toString())
    MusicImportScreen()
}


//val profile = UserProfile()
//var isHost = false

//@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
//@Composable
//@Preview
//@OptIn(ExperimentalMaterial3Api::class)
//fun HomePage() {
////    val track1=MediaItem(
////
////    );
//    val player = ExoPlayer.Builder(LocalContext.current).build()
////player.setMediaItem(
////    MediaItem.fromUri("https://cdn.freesound.org/previews/810/810337_5674468-lq.mp3")
////)
////    player.prepare()
//    var a by remember { mutableStateOf(false) }
//    var bottomBarAppIndex by remember { mutableIntStateOf(2) }
//
//    Scaffold(
//        modifier = Modifier
//            .fillMaxSize(),
//        topBar = {
//            TopAppBar(
//                actions = {
//                    IconButton(onClick = { println("Hello World") }) {
//                        Icon(
//                            Icons.Filled.Info, contentDescription = "no"
//                        )
//                    }
//                },
//                title = { Text("Hello") },
//            )
//        },
//        bottomBar = {
//            BottomAppBar(
//                modifier = Modifier
//            ) {
//                Row(
//                    modifier = Modifier
//                        .fillMaxSize(),
//                    horizontalArrangement = Arrangement.SpaceAround
//                ) {
//                    IconButton(onClick = { bottomBarAppIndex = 0 }) {
//                        Icon(
//                            Icons.Filled.Home,
//                            contentDescription = null
//                        )
//                    }
//                    IconButton(onClick = { bottomBarAppIndex = 1 }) {
//                        Icon(
//                            Icons.Filled.ConnectedTv,
//                            contentDescription = null
//                        )
//                    }
//                    IconButton(onClick = { bottomBarAppIndex = 2 }) {
//                        Icon(
//                            Icons.Filled.Person,
//                            contentDescription = null
//                        )
//                    }
//
//                }
//            }
//        }
//
//    ) {
//        Box(
//            modifier = Modifier
//                .padding(it)
//        ) {
//
//
//            when (bottomBarAppIndex) {
//                0 -> HostScreen()
//                1 -> ClientScreen()
//                2 -> ProfileScreen()
//                else ->
//                    Text("Defalut")
//            }
//        }
////        Column(modifier = Modifier
////                .fillMaxSize()
////                .padding(it),
////            verticalArrangement = Arrangement.Center,
////            horizontalAlignment = Alignment.CenterHorizontally
////
////        ) {
////            IconButton(onClick = {
////                println("hiiii")
////                a=a.not()
////                if (a)
////                player.play()
////                else
////                player.pause()
////            }) {
////                Icon(if(a)Icons.Filled.Pause else Icons.Filled.PlayArrow
////                    , contentDescription = "home")
////            }
////        }
//
//    }
//}
//

//@Composable
//fun HostScreen() {
//    var isHosting by remember { mutableStateOf(isHost) }
//    val ws = WSS(55555, profile.getName(), ExoPlayerManager(LocalContext.current));
//    Row {
//        Button(onClick = {
//            ws.start();
//            isHost = true;
//            isHosting = true;
//        }, enabled = isHosting.not()) { Text("Start") }
//        Button(
//            onClick = {
//                ws.stop();
////                ws.
//                isHost = false;
//                isHosting = false;
//            },
//            enabled = isHosting
//        ) { Text("Stop") }
//
//    }
//
//}
//
//@Composable
//fun ClientScreen() {
////    val ws = WSC("ws://192.168.202.68:55555");
//    val player = ExoPlayerManager(LocalContext.current)
//    val client = WSC(URI.create("ws://192.168.202.68:55555"), player)
////    client.onMessage()
//    Column {
//        Button(
//            onClick = {
//                try {
//                    client.connect();
//                } catch (e: Exception) {
//                    println(e)
//                }
//                //            ws.connectToServer("192.168.202.68",55555);
//                isHost = false;
////                Handler(Looper.getMainLooper()).post {
////                if (player.isPlaying().not())
////                    player.play()
////                else
////                    player.pause()
////                }
//            },
//        ) { Text("Connect") }
//        Button(
//            onClick = {
//                try {
//                    client.send("Hiiiiiiiiiiiiiiiiiiiiiiiiiiiiii");
//                } catch (e: Exception) {
//                    println(e)
//                }
//                //            ws.connectToServer("192.168.202.68",55555);
//                isHost = false;
//                val targetTime = System.currentTimeMillis() + 1000  // 300ms into the future
//                client.send("PLAY:$targetTime")
//            },
//        ) { Text("Send") }
//    }
//}
//
//@Composable
//fun ProfileScreen() {
//    var name by remember { mutableStateOf(profile.getName()) }
////    Text("Profile")
//    Column {
//        TextField(
//            value = name,
//            onValueChange = { name = it;profile.setName(name);println(name) },
//            readOnly = isHost
//        )
//    }
//}

@Composable
fun MusicPlayerScreen(){

}

