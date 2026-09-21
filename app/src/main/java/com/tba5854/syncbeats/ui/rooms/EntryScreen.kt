package com.tba5854.syncbeats.ui.rooms

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tba5854.syncbeats.settings.Settings
import com.tba5854.syncbeats.ui.viewmodel.SyncViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
        vm: SyncViewModel,
        onSettings: () -> Unit,
        onRoomEntered: () -> Unit
) {
    val connectionState by vm.connectionState.collectAsState()
    val availableRooms by vm.availableRooms.collectAsState()
    var roomIdInput by remember { mutableStateOf("") }
    var roomNameInput by remember { mutableStateOf("My Room") }

    LaunchedEffect(Unit) { vm.connect(Settings.config.serverIp) }

    LaunchedEffect(connectionState) {
        if (connectionState is SyncViewModel.ConnectionState.Connected) {
            vm.refreshRooms()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val dotAlpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec =
                    infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
            label = "dotAlpha"
    )

    val dotColor =
            when (connectionState) {
                is SyncViewModel.ConnectionState.Connected -> Color(0xFF4CAF50)
                is SyncViewModel.ConnectionState.Connecting -> Color(0xFFFFC107)
                else -> Color(0xFFF44336)
            }

    Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                        title = {},
                        actions = {
                            IconButton(onClick = onSettings) {
                                Icon(Icons.Filled.Settings, contentDescription = "Settings")
                            }
                        }
                )
            }
    ) { padding ->
        Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            Text(
                    "SyncBeats",
                    style =
                            MaterialTheme.typography.displayLarge.copy(
                                    fontWeight = FontWeight.Bold
                            ),
                    color = MaterialTheme.colorScheme.primary
            )

            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                        modifier =
                                Modifier.size(12.dp).drawBehind {
                                    drawCircle(
                                            color = dotColor.copy(alpha = dotAlpha),
                                            radius = size.minDimension / 2
                                    )
                                }
                )
                Text(
                        text =
                                when (connectionState) {
                                    is SyncViewModel.ConnectionState.Connected -> "Connected"
                                    is SyncViewModel.ConnectionState.Connecting -> "Connecting…"
                                    else -> "Disconnected"
                                },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                    value = roomNameInput,
                    onValueChange = { roomNameInput = it },
                    label = { Text("Room name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
            )
            Button(
                    onClick = {
                        vm.createRoom(roomNameInput)
                        onRoomEntered()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = connectionState is SyncViewModel.ConnectionState.Connected
            ) {
                Text("Create Room")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                    value = roomIdInput,
                    onValueChange = { roomIdInput = it },
                    label = { Text("Room ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                    onClick = {
                        vm.joinRoom(roomIdInput.trim())
                        onRoomEntered()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled =
                            roomIdInput.isNotBlank() &&
                                    connectionState is SyncViewModel.ConnectionState.Connected
            ) {
                Text("Join Room")
            }

            if (availableRooms.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                        "Available Rooms",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(availableRooms) { room ->
                        ListItem(
                                headlineContent = { Text(room.name) },
                                supportingContent = {
                                    Text("${room.memberCount} member${if (room.memberCount != 1) "s" else ""}")
                                },
                                modifier =
                                        Modifier.clickable {
                                            vm.joinRoom(room.roomId)
                                            onRoomEntered()
                                        }
                        )
                    }
                }
            }
        }
    }
}
