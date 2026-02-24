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
        playerManager.onNextCallback = {
            if (_roomState.value != null) queueNext()
        }
        playerManager.onPrevCallback = { if (_roomState.value != null) queuePrev() }

        viewModelScope.launch {
            while (true) {
                delay(500)
                _playbackPos.value = playerManager.getCurrentPositionSec()
            }
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
        playerManager.play()
        currentRoomId?.let { socket?.play(it, playerManager.getCurrentPositionSec()) }
    }

    fun pause() {
        playerManager.pause()
        currentRoomId?.let { socket?.pause(it, playerManager.getCurrentPositionSec()) }
    }

    fun seek(pos: Double) {
        _playbackPos.value = pos
        playerManager.seekToSec(pos)
        currentRoomId?.let { socket?.seek(it, pos) }
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
        _members.value = mapOf(cfg.userId to cfg.userName)
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
        resolveHash(state.trackHash)
        state.queue.forEach { resolveHash(it) }
    }

    override fun onTrackChanged(state: RoomState) {
        _roomState.value = state
        resolveHash(state.trackHash)
        _trackChangedBy.value = state.ownerId
        viewModelScope.launch {
            delay(3000)
            _trackChangedBy.value = null
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
                        val importResult =
                                MusicStorage.importFile(
                                        downloaded,
                                        _resolvedNames.value[hash] ?: hash
                                )

                        withContext(Dispatchers.Main) {
                            val title = _resolvedNames.value[hash]
                            playerManager.loadFile(importResult.file, title)
                            playerManager.onReady {
                                _durationMs.value = playerManager.getDurationMs() ?: 0L
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

    override fun onPlay(event: PlayEvent) {
        _syncStatus.value = SyncStatus.Adjusting
        val adjustedStart = NtpSync.adjustedTime(event.startAt)
        playerManager.playAtTime(adjustedStart)
        _syncStatus.value = SyncStatus.Synced
    }

    override fun onPause(event: PauseEvent) {
        playerManager.pause()
        playerManager.seekTo((event.position * 1000).toLong())
    }

    override fun onSeek(event: SeekEvent) {
        playerManager.seekToSec(event.position)
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
