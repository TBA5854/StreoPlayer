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
import com.tba5854.stereo_player.ui.theme.StreoPlayerTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StreoPlayerTheme {
                HomePage()
            }
        }
    }
}

val profile= UserProfile()
val isHost = false
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
@Preview
@OptIn(ExperimentalMaterial3Api::class)
fun HomePage() {
//    val track1=MediaItem(
//
//    );
    val player = ExoPlayer.Builder(LocalContext.current).build()
//player.setMediaItem(
//    MediaItem.fromUri("https://cdn.freesound.org/previews/810/810337_5674468-lq.mp3")
//)
//    player.prepare()
var a by remember { mutableStateOf(false) }
    var bottomBarAppIndex by remember { mutableIntStateOf(2) }

    Scaffold (
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            TopAppBar(
                actions = {
                    IconButton(onClick = { println("Hello World") }) {
                        Icon(
                            Icons.Filled.Info, contentDescription = "no"
                        )
                    }
                },
                title = { Text("Hello") },
            )
        },
        bottomBar = {
            BottomAppBar(
                modifier = Modifier
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    IconButton(onClick = { bottomBarAppIndex=0 }) { Icon(Icons.Filled.Home,contentDescription = null) }
                    IconButton(onClick = { bottomBarAppIndex=1 }) { Icon(Icons.Filled.ConnectedTv,contentDescription = null) }
                    IconButton(onClick = { bottomBarAppIndex=2 }) { Icon(Icons.Filled.Person,contentDescription = null) }

                }
            }
        }

    ) {
Box (
    modifier = Modifier
        .padding(it)
) {


            when(bottomBarAppIndex) {
                0->HostScreen()
                1->ClientScreen()
                2->ProfileScreen()
                else ->
                    Text("Defalut")
            }
}
//        Column(modifier = Modifier
//                .fillMaxSize()
//                .padding(it),
//            verticalArrangement = Arrangement.Center,
//            horizontalAlignment = Alignment.CenterHorizontally
//
//        ) {
//            IconButton(onClick = {
//                println("hiiii")
//                a=a.not()
//                if (a)
//                player.play()
//                else
//                player.pause()
//            }) {
//                Icon(if(a)Icons.Filled.Pause else Icons.Filled.PlayArrow
//                    , contentDescription = "home")
//            }
//        }

    }
}




@Composable
fun HostScreen(){
var isHosting by remember {mutableStateOf(isHost)}
    Text("Host")
}

@Composable
fun ClientScreen(){
    Text("Client")
}

@Composable
fun ProfileScreen(){
    var name by remember { mutableStateOf(profile.getName()) }
//    Text("Profile")
    Column {
        TextField(value = name, onValueChange = {  name=it;profile.setName(name);println(name)}, readOnly = isHost)
    }
}


