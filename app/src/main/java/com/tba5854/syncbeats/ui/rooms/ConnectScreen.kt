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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
fun ConnectScreen(vm: SyncViewModel, onRoomEntered: () -> Unit, onSettings: () -> Unit) {
    val connectionState by vm.connectionState.collectAsState()
    val availableRooms by vm.availableRooms.collectAsState()
    val roomState by vm.roomState.collectAsState()
    val members by vm.members.collectAsState()

    var roomName by remember { mutableStateOf("My Room") }
    var roomId by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.connect(Settings.config.serverIp) }
    LaunchedEffect(connectionState) {
        if (connectionState is SyncViewModel.ConnectionState.Connected) vm.refreshRooms()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val dotAlpha by
            infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                    label = "dotAlpha"
            )
    val dotColor =
            when (connectionState) {
                is SyncViewModel.ConnectionState.Connected -> Color(0xFF4CAF50)
                is SyncViewModel.ConnectionState.Connecting -> Color(0xFFFFC107)
                else -> Color(0xFFF44336)
            }
    val isConnected = connectionState is SyncViewModel.ConnectionState.Connected

    Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                        title = { Text("Connect", fontWeight = FontWeight.Bold) },
                        actions = {
                            Box(
                                    modifier =
                                            Modifier.size(32.dp).drawBehind {
                                                drawCircle(
                                                        color = dotColor.copy(alpha = dotAlpha),
                                                        radius = 6.dp.toPx()
                                                )
                                            }
                            )
                            IconButton(onClick = onSettings) {
                                Icon(Icons.Filled.Settings, contentDescription = "Settings")
                            }
                        },
                        colors =
                                TopAppBarDefaults.centerAlignedTopAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                )
                )
            }
    ) { padding ->
        LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (roomState != null) {
                item {
                    SectionCard(
                            icon = Icons.Filled.Group,
                            title = "Active Room: ${roomState?.name}"
                    ) {
                        Text(
                                "Connected as ${Settings.config.userName}",
                                style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                                "Members: ${members.size}",
                                style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                                "Queue: ${roomState?.queue?.size ?: 0} tracks",
                                style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                    onClick = { onRoomEntered() },
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.large
                            ) { Text("Open Player", fontWeight = FontWeight.SemiBold) }
                            OutlinedButton(
                                    onClick = { vm.leaveRoom() },
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.large,
                                    colors =
                                            androidx.compose.material3.ButtonDefaults
                                                    .outlinedButtonColors(
                                                            contentColor =
                                                                    MaterialTheme.colorScheme.error
                                                    )
                            ) { Text("Leave Room") }
                        }
                    }
                }
            } else {
                item {
                    SectionCard(icon = Icons.Filled.Add, title = "Create Room") {
                        OutlinedTextField(
                                value = roomName,
                                onValueChange = { roomName = it },
                                label = { Text("Room name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                                onClick = {
                                    vm.createRoom(roomName.trim())
                                    onRoomEntered()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = isConnected && roomName.isNotBlank(),
                                shape = MaterialTheme.shapes.large
                        ) { Text("Create Room", fontWeight = FontWeight.SemiBold) }
                    }
                }

                item {
                    SectionCard(icon = Icons.Filled.Link, title = "Join by ID") {
                        OutlinedTextField(
                                value = roomId,
                                onValueChange = { roomId = it },
                                label = { Text("Room ID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                                onClick = {
                                    vm.joinRoom(roomId.trim())
                                    onRoomEntered()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = isConnected && roomId.isNotBlank(),
                                shape = MaterialTheme.shapes.large
                        ) { Text("Join Room", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }

            item {
                Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                        shape = MaterialTheme.shapes.extraLarge
                ) {
                    Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                                Icons.Filled.Group,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                                "Available Rooms",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                        )
                        if (availableRooms.isNotEmpty()) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                    "${availableRooms.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(
                                onClick = { vm.refreshRooms() },
                                enabled = isConnected,
                                modifier = Modifier.size(32.dp)
                        ) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh Rooms") }
                    }
                    if (availableRooms.isEmpty()) {
                        Text(
                                if (isConnected) "No rooms found" else "Connect to see rooms",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            if (availableRooms.isNotEmpty()) {
                items(availableRooms, key = { it.roomId }) { room ->
                    ListItem(
                            headlineContent = { Text(room.name, fontWeight = FontWeight.Medium) },
                            supportingContent = {
                                Text(
                                        "${room.memberCount} member${if (room.memberCount != 1) "s" else ""}"
                                )
                            },
                            trailingContent = {
                                Icon(
                                        Icons.Filled.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier =
                                    Modifier.clickable(enabled = isConnected) {
                                                vm.joinRoom(room.roomId)
                                                onRoomEntered()
                                            }
                                            .padding(horizontal = 4.dp),
                            colors =
                                    ListItemDefaults.colors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        title: String,
        content: @Composable () -> Unit
) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
            shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}
