package com.tba5854.syncbeats.ui.sync

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.tba5854.syncbeats.core.player.ExoPlayerManager
import com.tba5854.syncbeats.core.storage.MusicStorage
import com.tba5854.syncbeats.settings.Settings
import com.tba5854.syncbeats.sync.server.*
import java.io.File
import kotlinx.coroutines.isActive

@Composable
fun SyncTestScreen() {
    val context = LocalContext.current
    val player = remember { ExoPlayerManager(context) }

    var log by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(false) }
    var activeRoomId by remember { mutableStateOf("") }
    var roomNameInput by remember { mutableStateOf("chill vibes") }
    var joinRoomInput by remember { mutableStateOf("") }
    var serverIpInput by remember { mutableStateOf(Settings.config.serverIp) }
    var appliedServerIp by remember { mutableStateOf(Settings.config.serverIp) }
    var ntpCount by remember { mutableStateOf(0) }
    var currentTrackHash by remember { mutableStateOf("") }
    var playerLoadedHash by remember { mutableStateOf("") }
    var isPlaying by remember { mutableStateOf(false) }
    var songs by remember { mutableStateOf(MusicStorage.getAll()) }
    var serverFiles by remember { mutableStateOf(listOf<RemoteFile>()) }
    var queue by remember { mutableStateOf(listOf<String>()) }
    var currentIndex by remember { mutableStateOf(-1) }

    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var socketRef by remember { mutableStateOf<SyncSocket?>(null) }

    fun appendLog(msg: String) {
        mainHandler.post { log = "$msg\n$log" }
    }

    val listener = remember {
        object : SyncListener {
            override fun onConnected() {
                mainHandler.post { connected = true }
                appendLog("Connected")
            }

            override fun onDisconnected(reason: String?) {
                mainHandler.post {
                    connected = false
                    isPlaying = false
                }
                appendLog("Disconnected: $reason")
            }

            override fun onRoomCreated(state: RoomState) {
                mainHandler.post { activeRoomId = state.roomId }
                appendLog("Room created: ${state.roomId}")
            }

            override fun onRoomJoined(state: RoomState) {
                mainHandler.post {
                    activeRoomId = state.roomId
                    currentTrackHash = state.trackHash
                    queue = state.queue
                    currentIndex = state.currentIndex
                }
                appendLog(
                        "Joined: ${state.roomId} (track: ${state.trackHash}, queue: ${state.queue.size} tracks)"
                )

                if (state.isPlaying && state.trackHash.isNotEmpty()) {
                    handleTrackSync(state.trackHash, true, state.position, state.startAt)
                }
            }

            override fun onRoomLeft(roomId: String) {
                mainHandler.post {
                    activeRoomId = ""
                    isPlaying = false
                }
                appendLog("Left room: $roomId")
            }

            override fun onMemberJoined(userId: String, username: String) {
                appendLog("Member joined: $username ($userId)")
            }

            override fun onMemberLeft(userId: String, username: String) {
                appendLog("Member left: $username ($userId)")
            }

            override fun onRoomState(state: RoomState) {
                mainHandler.post {
                    queue = state.queue
                    currentIndex = state.currentIndex
                }
                appendLog(
                        "State: playing=${state.isPlaying} pos=${state.position} track=${state.trackHash} queue=${state.queue.size}"
                )
                if (state.trackHash.isNotEmpty()) {
                    handleTrackSync(state.trackHash, state.isPlaying, state.position, state.startAt)
                }
            }

            override fun onTrackChanged(state: RoomState) {
                mainHandler.post {
                    currentTrackHash = state.trackHash
                    queue = state.queue
                    currentIndex = state.currentIndex
                }
                appendLog("Track changed: ${state.trackHash} (queue idx=${ state.currentIndex })")
                handleTrackSync(state.trackHash, state.isPlaying, state.position, state.startAt)
            }

            override fun onQueueUpdated(event: QueueUpdatedEvent) {
                mainHandler.post { queue = event.queue }
                appendLog("Queue updated: ${event.queue.size} tracks")
            }

            override fun onPlay(event: PlayEvent) {
                appendLog("Play: pos=${event.position}s startAt=${event.startAt}")
                handleTrackSync(event.trackHash, true, event.position, event.startAt)
            }

            private fun handleTrackSync(hash: String, playing: Boolean, pos: Double, start: Long) {
                val file = MusicStorage.getFileByHash(hash)
                if (file != null) {
                    mainHandler.post {
                        if (playerLoadedHash != hash) {
                            player.loadFile(file)
                            playerLoadedHash = hash
                            currentTrackHash = hash
                        }
                        if (playing) {
                            isPlaying = true
                            player.seekToSec(pos)
                            if (start > 0) {
                                val scheduleAt = NtpSync.adjustedTime(start)
                                player.playAtTime(scheduleAt)
                            } else {
                                player.play()
                            }
                        }
                    }
                } else {
                    appendLog("Track missing ($hash). Auto-downloading...")
                    Thread {
                                try {
                                    val temp = SyncApi.downloadFile(hash, context.cacheDir)
                                    MusicStorage.importFile(temp, hash)
                                    temp.delete()
                                    appendLog("Download complete. Requesting sync...")
                                    mainHandler.post { songs = MusicStorage.getAll() }
                                    socketRef?.requestState(activeRoomId)
                                } catch (e: Exception) {
                                    appendLog("Auto-download failed: ${e.message}")
                                }
                            }
                            .start()
                }
            }

            override fun onPause(event: PauseEvent) {
                mainHandler.post {
                    isPlaying = false
                    player.pause()
                    player.seekToSec(event.position)
                }
                appendLog("Paused at ${event.position}s")
            }

            override fun onSeek(event: SeekEvent) {
                mainHandler.post {
                    player.seekToSec(event.position)
                    if (event.startAt > 0) {
                        isPlaying = true
                        val scheduleAt = NtpSync.adjustedTime(event.startAt)
                        player.playAtTime(scheduleAt)
                    }
                }
                appendLog("Seek: pos=${event.position}s startAt=${event.startAt}")
            }

            override fun onNtpPong(pong: NtpPong) {
                mainHandler.post { ntpCount++ }
                appendLog("NTP #$ntpCount offset=${NtpSync.offset}ms")
            }

            override fun onSyncError(error: SyncError) {
                appendLog("ERROR [${error.code}]: ${error.message}")
            }
        }
    }

    val socket =
            remember(appliedServerIp) {
                SyncSocket(appliedServerIp, Settings.config.userId, listener).also {
                    socketRef = it
                }
            }

    DisposableEffect(Unit) {
        onDispose {
            socket.disconnect()
            player.release()
        }
    }

    val pickerLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri != null) {
                    val fileName = DocumentFile.fromSingleUri(context, uri)?.name ?: "Unknown"
                    Thread {
                                try {
                                    val stream = context.contentResolver.openInputStream(uri)
                                    if (stream != null) {
                                        val temp = File(context.cacheDir, "upload_tmp")
                                        temp.outputStream().use { stream.copyTo(it) }
                                        appendLog("Uploading $fileName...")
                                        SyncApi.uploadFile(temp, fileName)
                                        appendLog("Upload successful")
                                        temp.delete()
                                    }
                                } catch (e: Exception) {
                                    appendLog("Picker upload failed: ${e.message}")
                                }
                            }
                            .start()
                }
            }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Sync Test", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
                value = serverIpInput,
                onValueChange = { serverIpInput = it },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (serverIpInput != appliedServerIp) {
                        TextButton(
                                onClick = {
                                    Settings.update { it.serverIp = serverIpInput }
                                    SyncApi.reset()
                                    appliedServerIp = serverIpInput
                                    appendLog("Server IP applied: $serverIpInput")
                                }
                        ) { Text("Apply") }
                    }
                }
        )

        Text(
                "User: ${Settings.config.userId.take(8)}...",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { socket.connect() }, enabled = !connected) { Text("Connect") }
            Button(onClick = { socket.disconnect() }, enabled = connected) { Text("Disconnect") }
            Button(
                    onClick = {
                        NtpSync.reset()
                        mainHandler.post { ntpCount = 0 }
                        repeat(5) { i -> mainHandler.postDelayed({ socket.sendNtp() }, i * 200L) }
                    },
                    enabled = connected
            ) { Text("NTP ($ntpCount)") }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Text(
                "Room",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
        )

        Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                    value = roomNameInput,
                    onValueChange = { roomNameInput = it },
                    label = { Text("Room Name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
            )
            Button(
                    onClick = { socket.createRoom(roomNameInput) },
                    enabled = connected && activeRoomId.isEmpty()
            ) { Text("Create") }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                    value = joinRoomInput,
                    onValueChange = { joinRoomInput = it },
                    label = { Text("Room ID") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
            )
            Button(
                    onClick = { socket.joinRoom(joinRoomInput) },
                    enabled = connected && activeRoomId.isEmpty()
            ) { Text("Join") }
        }

        if (activeRoomId.isNotEmpty()) {
            Text(
                    "In room: $activeRoomId",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
            )
            Button(onClick = { socket.leaveRoom(activeRoomId) }) { Text("Leave Room") }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Text(
                "Tracks",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
        )

        if (songs.isEmpty()) {
            Text("No local songs. Import some first.", style = MaterialTheme.typography.bodySmall)
        }

        songs.forEach { song ->
            Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        song.title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                            onClick = {
                                Thread {
                                            try {
                                                val file = MusicStorage.getFileByHash(song.hash)
                                                if (file != null) {
                                                    SyncApi.uploadFile(file, song.title)
                                                    appendLog("Uploaded: ${song.title}")
                                                }
                                            } catch (e: Exception) {
                                                appendLog("Upload failed: ${e.message}")
                                            }
                                        }
                                        .start()
                            },
                            enabled = connected && activeRoomId.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("Upload", style = MaterialTheme.typography.labelSmall) }
                    FilledTonalButton(
                            onClick = { socket.setTrack(activeRoomId, song.hash) },
                            enabled = connected && activeRoomId.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("Set", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Server Files", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pickerLauncher.launch("audio/*") }, enabled = connected) {
                    Text("Upload")
                }
                Button(
                        onClick = {
                            Thread {
                                        try {
                                            val remoteFiles = SyncApi.listFiles()
                                            mainHandler.post { serverFiles = remoteFiles }
                                            appendLog("Fetched ${remoteFiles.size} server files")
                                        } catch (e: Exception) {
                                            appendLog("List failed: ${e.message}")
                                        }
                                    }
                                    .start()
                        },
                        enabled = connected
                ) { Text("Refresh") }
            }
        }

        if (serverFiles.isEmpty()) {
            Text("No files on server.", style = MaterialTheme.typography.bodySmall)
        }

        serverFiles.forEach { remote ->
            Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        remote.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                            onClick = {
                                Thread {
                                            try {
                                                val temp =
                                                        SyncApi.downloadFile(
                                                                remote.fileId,
                                                                context.cacheDir
                                                        )
                                                MusicStorage.importFile(temp, remote.fileName)
                                                temp.delete()
                                                appendLog("Downloaded: ${remote.fileName}")
                                                mainHandler.post { songs = MusicStorage.getAll() }
                                            } catch (e: Exception) {
                                                appendLog("Download failed: ${e.message}")
                                            }
                                        }
                                        .start()
                            },
                            enabled = connected,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("Download", style = MaterialTheme.typography.labelSmall) }
                    FilledTonalButton(
                            onClick = { socket.setTrack(activeRoomId, remote.fileId) },
                            enabled = connected && activeRoomId.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("Set", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Text(
                "Queue",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
        )

        if (queue.isEmpty()) {
            Text("Queue is empty.", style = MaterialTheme.typography.bodySmall)
        } else {
            queue.forEachIndexed { idx, hash ->
                val isActive = idx == currentIndex
                Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            "${idx + 1}. ${hash.take(12)}…" + if (isActive) " ▶" else "",
                            style =
                                    if (isActive)
                                            MaterialTheme.typography.bodyMedium.copy(
                                                    color = MaterialTheme.colorScheme.primary
                                            )
                                    else MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                            onClick = { socketRef?.queuePlayAt(activeRoomId, idx) },
                            enabled = connected && activeRoomId.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("Play", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                    onClick = { socketRef?.queuePrev(activeRoomId, currentIndex) },
                    enabled = connected && activeRoomId.isNotEmpty() && queue.isNotEmpty()
            ) { Text("⏮ Prev") }
            Button(
                    onClick = { socketRef?.queueNext(activeRoomId, currentIndex) },
                    enabled = connected && activeRoomId.isNotEmpty() && queue.isNotEmpty()
            ) { Text("Next ⏭") }
        }

        songs.forEach { song ->
            Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        song.title,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                        onClick = { socketRef?.queueAdd(activeRoomId, song.hash) },
                        enabled = connected && activeRoomId.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("+Q", style = MaterialTheme.typography.labelSmall) }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Text(
                "Playback",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
        )
        if (currentTrackHash.isNotEmpty()) {
            Text(
                    "Track: ${currentTrackHash.take(12)}...",
                    style = MaterialTheme.typography.labelSmall
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                    onClick = {
                        val pos = player.getCurrentPositionSec()
                        socketRef?.play(activeRoomId, pos)
                    },
                    enabled =
                            connected &&
                                    activeRoomId.isNotEmpty() &&
                                    currentTrackHash.isNotEmpty() &&
                                    !isPlaying
            ) { Text("Play") }
            Button(
                    onClick = {
                        val pos = player.getCurrentPositionSec()
                        socketRef?.pause(activeRoomId, pos)
                    },
                    enabled = connected && activeRoomId.isNotEmpty() && isPlaying
            ) { Text("Pause") }
            Button(
                    onClick = { socketRef?.seek(activeRoomId, 0.0) },
                    enabled =
                            connected && activeRoomId.isNotEmpty() && currentTrackHash.isNotEmpty()
            ) { Text("Restart") }
        }

        Spacer(modifier = Modifier.height(12.dp))

        var sliderPos by remember { mutableDoubleStateOf(0.0) }
        var isDragging by remember { mutableStateOf(false) }
        var duration by remember { mutableDoubleStateOf(0.0) }

        DisposableEffect(player) {
            player.onReady { duration = player.getDurationSec() }
            onDispose {}
        }

        LaunchedEffect(isPlaying, isDragging) {
            if (isPlaying && !isDragging) {
                while (isActive) {
                    withFrameMillis { sliderPos = player.getCurrentPositionSec() }
                }
            }
        }

        Column {
            Text(
                    "Seek: ${sliderPos.toInt()}s / ${duration.toInt()}s",
                    style = MaterialTheme.typography.labelMedium
            )
            Slider(
                    value = sliderPos.toFloat(),
                    onValueChange = {
                        isDragging = true
                        sliderPos = it.toDouble()
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        socketRef?.seek(activeRoomId, sliderPos)
                    },
                    valueRange = 0f..(if (duration > 0) duration.toFloat() else 100f),
                    enabled =
                            connected && activeRoomId.isNotEmpty() && currentTrackHash.isNotEmpty()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Text(
                "Log",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(log, style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth())
    }
}
