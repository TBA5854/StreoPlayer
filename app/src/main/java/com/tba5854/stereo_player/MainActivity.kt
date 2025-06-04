package com.tba5854.stereo_player

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.media3.common.MediaItem
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


@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
@Preview
@OptIn(ExperimentalMaterial3Api::class)
fun HomePage() {
//    val track1=MediaItem(
//
//    );
    val player = ExoPlayer.Builder(LocalContext.current).build()
player.setMediaItem(
    MediaItem.fromUri("https://cdn.freesound.org/previews/810/810337_5674468-lq.mp3")
)
    player.prepare()
var a by remember { mutableStateOf(false) }
    Scaffold (
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
        }//appbar
    ) {



        Column(modifier = Modifier
                .fillMaxSize()
                .padding(it),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally

        ) {
            IconButton(onClick = {
                println("hiiii")
                a=a.not()
                if (a)
                player.play()
                else
                player.pause()
            }) {
                Icon(if(a)Icons.Filled.Pause else Icons.Filled.PlayArrow
                    , contentDescription = "home")
            }
        }
    }
}