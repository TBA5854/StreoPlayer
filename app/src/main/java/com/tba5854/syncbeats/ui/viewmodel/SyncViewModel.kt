package com.tba5854.syncbeats.ui.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tba5854.syncbeats.core.player.ExoPlayerManager
import com.tba5854.syncbeats.core.storage.MusicStorage
import com.tba5854.syncbeats.settings.Settings
import com.tba5854.syncbeats.sync.server.NtpPong
import com.tba5854.syncbeats.sync.server.NtpSync
import com.tba5854.syncbeats.sync.server.PauseEvent
import com.tba5854.syncbeats.sync.server.PlayEvent
import com.tba5854.syncbeats.sync.server.QueueUpdatedEvent
import com.tba5854.syncbeats.sync.server.RoomInfo
import com.tba5854.syncbeats.sync.server.RoomState
import com.tba5854.syncbeats.sync.server.SeekEvent
import com.tba5854.syncbeats.sync.server.SyncApi
import com.tba5854.syncbeats.sync.server.SyncError
import com.tba5854.syncbeats.sync.server.SyncListener
import com.tba5854.syncbeats.sync.server.SyncSocket
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SyncViewModel
@Inject
constructor(
        @ApplicationContext private val context: Context,
        private val savedState: SavedStateHandle,
        private val playerManager: com.tba5854.syncbeats.core.player.ExoPlayerManager
) : ViewModel(), SyncListener {

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        data class Connected(val latencyMs: Long = 0) : ConnectionState()
    }

    sealed class SyncStatus {
        object Synced : SyncStatus()
        object Adjusting : SyncStatus()
        object Reconnecting : SyncStatus()
        data class Uploading(val filename: String) : SyncStatus()
        data class Downloading(val filename: String) : SyncStatus()
    }

    private var savedServerIp: String
        get() = savedState["serverIp"] ?: Settings.config.serverIp
        set(v) {
            savedState["serverIp"] = v
        }

    private var savedRoomId: String?
        get() = savedState["roomId"]
        set(v) {
            savedState["roomId"] = v
        }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _roomState = MutableStateFlow<RoomState?>(null)
    val roomState: StateFlow<RoomState?> = _roomState

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Adjusting)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus

    private val _playbackPos = MutableStateFlow(0.0)
    val playbackPos: StateFlow<Double> = _playbackPos

    val isPlaying: StateFlow<Boolean> = playerManager.isPlayingFlow

    private val _trackChangedBy = MutableStateFlow<String?>(null)
    val trackChangedBy: StateFlow<String?> = _trackChangedBy

    private val _availableRooms = MutableStateFlow<List<RoomInfo>>(emptyList())
    val availableRooms: StateFlow<List<RoomInfo>> = _availableRooms

    private val _members = MutableStateFlow<Map<String, String>>(emptyMap())
    val members: StateFlow<Map<String, String>> = _members

    private val _resolvedNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val resolvedNames: StateFlow<Map<String, String>> = _resolvedNames

    private val activeUploads = mutableSetOf<String>()
    private val activeDownloads = mutableSetOf<String>()

    private val _currentSoloTrack = MutableStateFlow<String?>(null)
    val currentSoloTrack: StateFlow<String?> = _currentSoloTrack

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    val navStack: androidx.compose.runtime.snapshots.SnapshotStateList<String> = run {
        val saved: ArrayList<String>? = savedState["navStack"]
        val start =
                if (Settings.config.userName == "Guest") listOf("Onboarding") else listOf("Library")
        androidx.compose.runtime.mutableStateListOf(*(saved ?: start).toTypedArray())
    }

    private fun saveStack() {
        savedState["navStack"] = ArrayList(navStack)
    }

    fun pushRoute(route: String) {
        navStack.add(route)
        saveStack()
    }

    fun popRoute(): Boolean {
        if (navStack.size <= 1) return false
        navStack.removeLastOrNull()
        saveStack()
        return true
    }

    fun switchTab(tab: String) {
        val idx = navStack.indexOfLast { it == tab }
        if (idx >= 0) {
            repeat(navStack.size - idx - 1) { navStack.removeLastOrNull() }
        } else {
            navStack.clear()
            navStack.add(tab)
        }
        saveStack()
    }

    private fun resolveHash(hash: String?) {
        if (hash.isNullOrBlank()) return
        if (!_resolvedNames.value.containsKey(hash)) {
            viewModelScope.launch(Dispatchers.IO) {
                val entity = MusicStorage.getByHash(hash)
                val name = entity?.title ?: hash
                _resolvedNames.value = _resolvedNames.value + (hash to name)
            }
        }
    }

    private var socket: SyncSocket? = null
    private var currentRoomId: String? = null

    init {
        playerManager.onNextCallback = { if (_roomState.value != null) queueNext() }
        playerManager.onPrevCallback = { if (_roomState.value != null) queuePrev() }
        playerManager.onSeekCallback = { broadcastCurrentStateIfHost() }

        viewModelScope.launch {
            while (true) {
                delay(500)
                _playbackPos.value = playerManager.getCurrentPositionSec()
            }
        }

        viewModelScope.launch {
            while (true) {
                if (_connectionState.value is ConnectionState.Connected) {
                    socket?.sendNtp()
                }
                delay(5500)
            }
        }
    }

    private fun broadcastCurrentStateIfHost() {
        val room = _roomState.value ?: return
        if (room.ownerId != Settings.config.userId) return
        if (isApplyingRemoteEvent) return
        val pos = playerManager.getCurrentPositionSec()
        // Use playWhenReady rather than isPlaying() — the latter goes false during buffering
        // (e.g. mid-seek), which would incorrectly broadcast sync:pause while the host is still
        // intending to play.
        if (playerManager.isPlayWhenReady()) {
            val localStartMs = System.currentTimeMillis() + 250L
            val startAt = localStartMs + NtpSync.offset - (pos * 1000).toLong()
            socket?.play(room.roomId, pos, startAt)

            // Re-schedule the playback to mathematically mirror the clients' delay so the host
            // starts perfectly synced instead of 250ms ahead.
            playerManager.pause()
            playerManager.playAtTime(localStartMs)
        } else {
            socket?.pause(room.roomId, pos)
        }
    }

    private fun syncStateFromRoom(state: RoomState) {
        if (state.ownerId == Settings.config.userId) return
        if (state.isPlaying) {
            val adjustedStart = NtpSync.adjustedTime(state.startAt)
            val elapsedMs = System.currentTimeMillis() - adjustedStart
            val basePosMs = (state.position * 1000).toLong()
            val expectedPosMs = basePosMs + elapsedMs
            val currentPosMs = playerManager.getCurrentPositionMs()
            val driftMs = Math.abs(expectedPosMs - currentPosMs)

            if (elapsedMs > 0) {
                // The play event timestamp has already passed.
                // Seek only if the drift is noticeable (>50ms) to avoid buffering stutters on
                // micro-adjustments.
                if (driftMs > 50L) {
                    playerManager.seekTo(expectedPosMs)
                }
                playerManager.play()
            } else {
                // The play event timestamp is in the future.
                // Seek to the base position and wait for the timestamp to invoke play().
                if (Math.abs(basePosMs - currentPosMs) > 50L) {
                    playerManager.seekTo(basePosMs)
                }
                playerManager.playAtTime(adjustedStart)
            }
        } else {
            playerManager.pause()
            playerManager.seekToSec(state.position)
        }
    }

    fun connect(serverIp: String = savedServerIp) {
        if (_connectionState.value !is ConnectionState.Disconnected) return
        _connectionState.value = ConnectionState.Connecting
        savedServerIp = serverIp
        Settings.update { it.serverIp = serverIp }
        SyncApi.reset()
        val cfg = Settings.config
        socket = SyncSocket(serverIp, cfg.userId, this, cfg.userName)
        socket?.connect()
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
        playerManager.stopAndClear()
        _connectionState.value = ConnectionState.Disconnected
        _roomState.value = null
        _members.value = emptyMap()
        _resolvedNames.value = emptyMap()
        activeUploads.clear()
        activeDownloads.clear()
        currentRoomId = null
        savedRoomId = null
        _currentSoloTrack.value = null
    }

    fun createRoom(name: String) {
        socket?.createRoom(name)
    }

    fun joinRoom(roomId: String) {
        socket?.joinRoom(roomId)
    }

    fun leaveRoom() {
        currentRoomId?.let { socket?.leaveRoom(it) }
        savedRoomId = null
    }

    fun refreshRooms() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { SyncApi.listRooms() }.onSuccess { _availableRooms.value = it }
        }
    }

    fun setTrack(hash: String) {
        currentRoomId?.let { socket?.setTrack(it, hash) }
    }

    fun play() {
        currentRoomId?.let { roomId ->
            val pos = playerManager.getCurrentPositionSec()
            val localStartMs = System.currentTimeMillis() + 250L
            val startAt = localStartMs + NtpSync.offset - (pos * 1000).toLong()
            socket?.play(roomId, pos, startAt)
            playerManager.playAtTime(localStartMs)
        }
                ?: run { playerManager.play() }
    }

    fun pause() {
        playerManager.pause()
        currentRoomId?.let { socket?.pause(it, playerManager.getCurrentPositionSec()) }
    }

    fun seek(pos: Double) {
        _playbackPos.value = pos
        playerManager.seekToSec(pos)
        currentRoomId?.let { roomId ->
            // When playing, schedule 250ms ahead and include start_at so receivers can
            // NTP-adjust playback position.
            val startAt =
                    if (playerManager.isPlaying()) {
                        val localStartMs = System.currentTimeMillis() + 250L
                        playerManager.pause()
                        playerManager.playAtTime(localStartMs)
                        localStartMs + NtpSync.offset - (pos * 1000).toLong()
                    } else 0L
            socket?.seek(roomId, pos, startAt)
        }
        broadcastCurrentStateIfHost()
    }

    fun queueAdd(hash: String) {
        currentRoomId?.let { socket?.queueAdd(it, hash) }
    }

    fun queueRemove(index: Int) {
        currentRoomId?.let { socket?.queueRemove(it, index) }
    }

    fun queueMove(from: Int, to: Int) {
        currentRoomId?.let { socket?.queueMove(it, from, to) }
    }

    fun queueNext() {
        currentRoomId?.let { r -> roomState.value?.currentIndex?.let { socket?.queueNext(r, it) } }
    }

    fun queuePrev() {
        currentRoomId?.let { r -> roomState.value?.currentIndex?.let { socket?.queuePrev(r, it) } }
    }

    fun queuePlayAt(index: Int) {
        currentRoomId?.let { socket?.queuePlayAt(it, index) }
    }

    fun updateServerIp(ip: String) {
        savedServerIp = ip
        Settings.update { it.serverIp = ip }
        SyncApi.reset()
    }

    fun resync() {
        currentRoomId?.let { socket?.requestState(it) }
    }

    fun playSolo(hash: String) {
        _currentSoloTrack.value = hash
        viewModelScope.launch(Dispatchers.IO) {
            MusicStorage.getFileByHash(hash)?.let { file ->
                withContext(Dispatchers.Main) {
                    val title = _resolvedNames.value[hash]
                    playerManager.loadFile(file, title)
                    playerManager.onReady {
                        _durationMs.value = playerManager.getDurationMs() ?: 0L
                    }
                    playerManager.play()
                }
            }
        }
    }

    fun soloPlayPause() {
        if (playerManager.isPlaying() == true) playerManager.pause() else playerManager.play()
    }

    fun soloSeek(posMs: Long) {
        playerManager.seekTo(posMs)
    }

    fun soloIsPlaying(): Boolean = playerManager.isPlaying()

    override fun onConnected() {
        _connectionState.value = ConnectionState.Connected()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val remoteFiles = SyncApi.listFiles()
                withContext(Dispatchers.Main) {
                    val newNames = remoteFiles.associate { it.fileId to it.fileName }
                    _resolvedNames.value = _resolvedNames.value + newNames
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDisconnected(reason: String?) {
        _connectionState.value = ConnectionState.Disconnected
        _syncStatus.value = SyncStatus.Reconnecting
    }

    override fun onRoomCreated(state: RoomState) {
        _roomState.value = state
        currentRoomId = state.roomId
        savedRoomId = state.roomId
        val cfg = Settings.config
        _members.value = mapOf(cfg.userId to cfg.userName)
    }

    override fun onRoomJoined(state: RoomState) {
        _roomState.value = state
        currentRoomId = state.roomId
        savedRoomId = state.roomId
        val cfg = Settings.config

        // Build the members map from the room state's user list
        val joinedMembers = state.users.associate { it.userId to it.username }.toMutableMap()
        // Ensure the current user is always included
        joinedMembers[cfg.userId] = cfg.userName
        _members.value = joinedMembers

        state.queue.forEach { resolveHash(it) }
        loadTrackAndSync(state, showChangedBy = false)
    }

    override fun onRoomLeft(roomId: String) {
        _roomState.value = null
        _members.value = emptyMap()
        currentRoomId = null
        savedRoomId = null
    }

    override fun onMemberJoined(userId: String, username: String) {
        _members.value = _members.value + (userId to username)
    }

    override fun onMemberLeft(userId: String, username: String) {
        _members.value = _members.value - userId
    }

    override fun onRoomState(state: RoomState) {
        _roomState.value = state
        val cfg = Settings.config
        val stateMembers = state.users.associate { it.userId to it.username }.toMutableMap()
        stateMembers[cfg.userId] = cfg.userName
        _members.value = stateMembers

        resolveHash(state.trackHash)
        state.queue.forEach { resolveHash(it) }
        syncStateFromRoom(state)
    }

    override fun onTrackChanged(state: RoomState) {
        _roomState.value = state
        resolveHash(state.trackHash)
        loadTrackAndSync(state, showChangedBy = true)
    }

    /**
     * Loads the track from [state] (downloading if necessary) and, once ready, applies the room's
     * playback state. Used on both track-change and room-join events.
     */
    private fun loadTrackAndSync(state: RoomState, showChangedBy: Boolean) {
        if (showChangedBy) {
            _trackChangedBy.value = state.ownerId
            viewModelScope.launch {
                delay(3000)
                _trackChangedBy.value = null
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val hash = state.trackHash
            if (hash.isNullOrBlank()) return@launch

            val localFile = MusicStorage.getFileByHash(hash)
            if (localFile != null) {
                withContext(Dispatchers.Main) {
                    val title = _resolvedNames.value[hash]
                    playerManager.loadFile(localFile, title)
                    playerManager.onReady {
                        _durationMs.value = playerManager.getDurationMs() ?: 0L
                        if (state.ownerId == Settings.config.userId) {
                            broadcastCurrentStateIfHost()
                        } else {
                            syncStateFromRoom(state)
                        }
                    }
                }
            } else {
                if (!activeDownloads.contains(hash)) {
                    activeDownloads.add(hash)
                    try {
                        withContext(Dispatchers.Main) {
                            _syncStatus.value =
                                    SyncStatus.Downloading(_resolvedNames.value[hash] ?: hash)
                        }
                        val downloaded = SyncApi.downloadFile(hash, context.cacheDir)
                        val importResult = MusicStorage.importFile(downloaded, downloaded.name)
                        withContext(Dispatchers.Main) {
                            // Cache the true filename so the UI updates to show it instead of the
                            // hash
                            val realTitle = downloaded.name
                            _resolvedNames.value = _resolvedNames.value + (hash to realTitle)

                            playerManager.loadFile(importResult.file, realTitle)
                            playerManager.onReady {
                                _durationMs.value = playerManager.getDurationMs() ?: 0L
                                if (state.ownerId == Settings.config.userId) {
                                    broadcastCurrentStateIfHost()
                                } else {
                                    syncStateFromRoom(state)
                                }
                            }
                            if (_syncStatus.value is SyncStatus.Downloading) {
                                _syncStatus.value = SyncStatus.Synced
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        activeDownloads.remove(hash)
                    }
                }
            }
        }
    }

    override fun onQueueUpdated(event: QueueUpdatedEvent) {
        _roomState.value = _roomState.value?.copy(queue = event.queue)
        event.queue.forEach { resolveHash(it) }
    }

    private var lastSeekBroadcastTime = 0L
    /**
     * True while we are applying a remote socket event locally. Prevents seek callbacks from
     * re-broadcasting the same event back to the server, which would create an infinite loop.
     */
    private var isApplyingRemoteEvent = false

    override fun onPlay(event: PlayEvent) {
        viewModelScope.launch(Dispatchers.Main) {
            _syncStatus.value = SyncStatus.Adjusting
            lastSeekBroadcastTime = System.currentTimeMillis()
            isApplyingRemoteEvent = true
            try {
                val adjustedStart = NtpSync.adjustedTime(event.startAt)
                val elapsedMs = System.currentTimeMillis() - adjustedStart
                val basePosMs = (event.position * 1000).toLong()
                val expectedPosMs = basePosMs + elapsedMs
                val currentPosMs = playerManager.getCurrentPositionMs()
                val driftMs = Math.abs(expectedPosMs - currentPosMs)

                if (elapsedMs > 0) {
                    if (driftMs > 50L) {
                        playerManager.seekTo(expectedPosMs)
                    }
                    playerManager.play()
                } else {
                    if (Math.abs(basePosMs - currentPosMs) > 50L) {
                        playerManager.seekTo(basePosMs)
                    }
                    playerManager.playAtTime(adjustedStart)
                }
            } finally {
                isApplyingRemoteEvent = false
            }
            _syncStatus.value = SyncStatus.Synced
        }
    }

    override fun onPause(event: PauseEvent) {
        viewModelScope.launch(Dispatchers.Main) {
            lastSeekBroadcastTime = System.currentTimeMillis()
            isApplyingRemoteEvent = true
            try {
                playerManager.pause()
                playerManager.seekTo((event.position * 1000).toLong())
            } finally {
                isApplyingRemoteEvent = false
            }
        }
    }

    override fun onSeek(event: SeekEvent) {
        viewModelScope.launch(Dispatchers.Main) {
            lastSeekBroadcastTime = System.currentTimeMillis()
            isApplyingRemoteEvent = true
            try {
                if (event.startAt > 0) {
                    // Host was playing — seek to position then let NTP-adjusted clock take over,
                    // exactly like onPlay does, so network latency is compensated.
                    playerManager.seekToSec(event.position)
                    val adjustedStart = NtpSync.adjustedTime(event.startAt)
                    playerManager.playAtTime(adjustedStart)
                } else {
                    // Host was paused — simple seek, no timing compensation needed.
                    playerManager.seekToSec(event.position)
                }
            } finally {
                isApplyingRemoteEvent = false
            }
        }
    }

    override fun onNtpPong(pong: NtpPong) {}

    override fun onSyncError(error: SyncError) {
        val msg = error.message
        if (msg.contains("not found in library")) {
            val hashMatch = """track "(.*?)" not found""".toRegex().find(msg)
            val hash = hashMatch?.groupValues?.getOrNull(1)

            if (hash != null && !activeUploads.contains(hash)) {
                activeUploads.add(hash)
                viewModelScope.launch(Dispatchers.IO) {
                    MusicStorage.getFileByHash(hash)?.let { file ->
                        try {
                            val filename = _resolvedNames.value[hash] ?: file.name
                            withContext(Dispatchers.Main) {
                                _syncStatus.value = SyncStatus.Uploading(filename)
                            }
                            SyncApi.uploadFile(file, filename)
                            withContext(Dispatchers.Main) {
                                when (error.code) {
                                    "QUEUE_ADD_FAILED" -> {
                                        val wasEmpty = _roomState.value?.queue?.isEmpty() == true
                                        queueAdd(hash)
                                        if (wasEmpty) {
                                            setTrack(hash)
                                        }
                                    }
                                    "TRACK_SET_FAILED" -> setTrack(hash)
                                    "QUEUE_PLAY_AT_FAILED" -> {
                                        val idx = _roomState.value?.queue?.indexOf(hash) ?: -1
                                        if (idx >= 0) queuePlayAt(idx) else setTrack(hash)
                                    }
                                    else -> {
                                        val idx = _roomState.value?.queue?.indexOf(hash) ?: -1
                                        if (idx == -1) {
                                            val wasEmpty =
                                                    _roomState.value?.queue?.isEmpty() == true
                                            queueAdd(hash)
                                            if (wasEmpty) setTrack(hash)
                                        }
                                    }
                                }
                                if (_syncStatus.value is SyncStatus.Uploading) {
                                    _syncStatus.value = SyncStatus.Synced
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            activeUploads.remove(hash)
                            withContext(Dispatchers.Main) {
                                if (_syncStatus.value is SyncStatus.Uploading) {
                                    _syncStatus.value = SyncStatus.Synced
                                }
                            }
                        }
                    }
                            ?: run { activeUploads.remove(hash) }
                }
            }
        }
    }

    override fun onCleared() {
        disconnect()
    }
}
