package com.tba5854.syncbeats.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.tba5854.syncbeats.ui.library.LibraryScreen
import com.tba5854.syncbeats.ui.library.SoloPlayerScreen
import com.tba5854.syncbeats.ui.onboarding.OnboardingScreen
import com.tba5854.syncbeats.ui.rooms.ConnectScreen
import com.tba5854.syncbeats.ui.rooms.RoomOverviewScreen
import com.tba5854.syncbeats.ui.settings.SettingsScreen
import com.tba5854.syncbeats.ui.viewmodel.SyncViewModel

@Composable
fun AppNav() {
    val vm: SyncViewModel = hiltViewModel()
    val backStack = vm.navStack
    val topRoute = backStack.lastOrNull()
    val showTabs = topRoute == "Library" || topRoute == "Connect"

    Scaffold(
            bottomBar = {
                if (showTabs) {
                    NavigationBar {
                        NavigationBarItem(
                                selected = topRoute == "Library",
                                onClick = { vm.switchTab("Library") },
                                icon = {
                                    Icon(Icons.Filled.LibraryMusic, contentDescription = "Library")
                                },
                                label = { Text("Library") }
                        )
                        NavigationBarItem(
                                selected = topRoute == "Connect",
                                onClick = { vm.switchTab("Connect") },
                                icon = {
                                    Icon(Icons.Filled.People, contentDescription = "Connect")
                                },
                                label = { Text("Connect") }
                        )
                    }
                }
            }
    ) { padding ->
        val context = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.runtime.LaunchedEffect(Unit) {
            com.tba5854.syncbeats.IntentHandler.pendingAudioUris.collect { uri ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val entity =
                            com.tba5854.syncbeats.core.storage.MusicStorage.importUri(context, uri)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        vm.playSolo(entity.hash)
                        vm.switchTab("Library")
                        vm.pushRoute("SoloPlayer")
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    NavDisplay(
                            backStack = backStack,
                            onBack = { vm.popRoute() },
                            entryProvider = { key ->
                                when (key) {
                                    "Onboarding" ->
                                            NavEntry(key) {
                                                OnboardingScreen(
                                                        onDone = { vm.pushRoute("Library") }
                                                )
                                            }
                                    "Library" ->
                                            NavEntry(key) {
                                                LibraryScreen(
                                                        vm = vm,
                                                        onOpenPlayer = {
                                                            vm.pushRoute("SoloPlayer")
                                                        },
                                                        onSettings = { vm.pushRoute("Settings") }
                                                )
                                            }
                                    "SoloPlayer" ->
                                            NavEntry(key) {
                                                SoloPlayerScreen(
                                                        vm = vm,
                                                        onBack = { vm.popRoute() }
                                                )
                                            }
                                    "Connect" ->
                                            NavEntry(key) {
                                                ConnectScreen(
                                                        vm = vm,
                                                        onRoomEntered = {
                                                            vm.pushRoute("RoomOverview")
                                                        },
                                                        onSettings = { vm.pushRoute("Settings") }
                                                )
                                            }
                                    "RoomOverview" -> NavEntry(key) { RoomOverviewScreen(vm = vm) }
                                    "Settings" ->
                                            NavEntry(key) {
                                                SettingsScreen(vm = vm, onBack = { vm.popRoute() })
                                            }
                                    else -> NavEntry(key) {}
                                }
                            }
                    )
                }

                val roomState by vm.roomState.collectAsState()
                val currentSoloTrack by vm.currentSoloTrack.collectAsState()
                val isPlayingFlow by vm.isPlaying.collectAsState()
                val playbackPosFlow by vm.playbackPos.collectAsState()
                val durationMsFlow by vm.durationMs.collectAsState()
                val resolvedNames by vm.resolvedNames.collectAsState()

                val isActiveRoom = roomState != null && !roomState?.trackHash.isNullOrBlank()
                val isActiveSolo = !currentSoloTrack.isNullOrBlank()

                if (isActiveRoom || isActiveSolo) {
                    val isSoloPlaying = vm.soloIsPlaying()
                    val trackHash = if (isActiveRoom) roomState?.trackHash else currentSoloTrack
                    val name = trackHash?.let { resolvedNames[it] ?: it }

                    val title = if (!name.isNullOrBlank()) name else "No track"
                    val isPlaying = if (isActiveRoom) isPlayingFlow else isSoloPlaying

                    Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier =
                                    Modifier.fillMaxWidth().clickable {
                                        if (isActiveRoom) vm.pushRoute("RoomOverview")
                                        else vm.pushRoute("SoloPlayer")
                                    }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                    modifier =
                                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.MusicNote, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { if (isActiveRoom) vm.queuePrev() }) {
                                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Prev")
                                }
                                IconButton(
                                        onClick = {
                                            if (isActiveRoom) {
                                                if (isPlaying) vm.pause() else vm.play()
                                            } else {
                                                vm.soloPlayPause()
                                            }
                                        }
                                ) {
                                    Icon(
                                            imageVector =
                                                    if (isPlaying) Icons.Filled.Pause
                                                    else Icons.Filled.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play"
                                    )
                                }

                                IconButton(onClick = { if (isActiveRoom) vm.queueNext() }) {
                                    Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                                }
                            }

                            val progress =
                                    if (durationMsFlow > 0)
                                            (playbackPosFlow / (durationMsFlow / 1000.0)).toFloat()
                                    else 0f
                            LinearProgressIndicator(
                                    progress = { progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
