package com.tba5854.syncbeats.ui.rooms

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tba5854.syncbeats.core.storage.MusicStorage
import com.tba5854.syncbeats.settings.Settings
import com.tba5854.syncbeats.ui.viewmodel.SyncViewModel
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomOverviewScreen(vm: SyncViewModel) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        val roomState by vm.roomState.collectAsState()
        val members by vm.members.collectAsState()
        val syncStatus by vm.syncStatus.collectAsState()
        val playbackPos by vm.playbackPos.collectAsState()
        val durationMs by vm.durationMs.collectAsState()
        val trackChangedBy by vm.trackChangedBy.collectAsState()
        val resolvedNames by vm.resolvedNames.collectAsState()
        val isPlaying by vm.isPlaying.collectAsState()

        val selfId = Settings.config.userId
        val queue = roomState?.queue ?: emptyList()
        val currentIndex = roomState?.currentIndex ?: -1

        var showQueue by remember { mutableStateOf(false) }
        var showMembers by remember { mutableStateOf(false) }
        var showUpload by remember { mutableStateOf(false) }

        var dragOffset by remember { mutableStateOf<Float?>(null) }
        var sliderPos by remember { mutableStateOf<Float?>(null) }
        val displayPos: Double =
                sliderPos?.toDouble()
                        ?: dragOffset?.let {
                                (playbackPos + it / 10f).coerceIn(0.0, durationMs / 1000.0)
                        }
                                ?: playbackPos

        val queueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val membersSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val uploadSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        val infiniteTransition = rememberInfiniteTransition(label = "glow")
        val glowAlpha by
                infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 0.9f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation = tween(1200),
                                        repeatMode = RepeatMode.Reverse
                                ),
                        label = "glowAlpha"
                )
        val glowRadius by
                infiniteTransition.animateFloat(
                        initialValue = 8f,
                        targetValue = 24f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation = tween(1200),
                                        repeatMode = RepeatMode.Reverse
                                ),
                        label = "glowRadius"
                )

        Scaffold(
                topBar = {
                        CenterAlignedTopAppBar(
                                navigationIcon = {
                                        IconButton(onClick = { vm.switchTab("Connect") }) {
                                                Icon(
                                                        Icons.AutoMirrored.Filled.ArrowBack,
                                                        contentDescription = "Back"
                                                )
                                        }
                                },
                                title = {
                                        Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                                Text(
                                                        roomState?.name ?: "Room",
                                                        style = MaterialTheme.typography.titleLarge
                                                )
                                                if (roomState?.ownerId == selfId) {
                                                        Icon(
                                                                Icons.Filled.Star,
                                                                contentDescription = "Host",
                                                                tint = Color(0xFFFFD700),
                                                                modifier = Modifier.size(18.dp)
                                                        )
                                                }
                                        }
                                },
                                actions = {
                                        IconButton(
                                                onClick = {
                                                        vm.leaveRoom()
                                                        vm.switchTab("Connect")
                                                }
                                        ) {
                                                Icon(
                                                        Icons.AutoMirrored.Filled.ExitToApp,
                                                        contentDescription = "Leave Room",
                                                        tint = MaterialTheme.colorScheme.error
                                                )
                                        }
                                        IconButton(onClick = { showUpload = true }) {
                                                Icon(
                                                        Icons.Filled.CloudUpload,
                                                        contentDescription = "Upload Music"
                                                )
                                        }
                                        IconButton(onClick = { showMembers = true }) {
                                                Icon(
                                                        Icons.Filled.Group,
                                                        contentDescription = "Members"
                                                )
                                        }
                                        IconButton(onClick = { showQueue = true }) {
                                                Icon(
                                                        Icons.AutoMirrored.Filled.QueueMusic,
                                                        contentDescription = "Queue"
                                                )
                                        }
                                }
                        )
                }
        ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        Column(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .padding(horizontal = 24.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                                val chipLabel =
                                        when (val status = syncStatus) {
                                                is SyncViewModel.SyncStatus.Synced -> "Synced ✓"
                                                is SyncViewModel.SyncStatus.Adjusting ->
                                                        "Adjusting…"
                                                is SyncViewModel.SyncStatus.Reconnecting ->
                                                        "Reconnecting"
                                                is SyncViewModel.SyncStatus.Uploading ->
                                                        "Uploading: ${status.filename.take(15)}…"
                                                is SyncViewModel.SyncStatus.Downloading ->
                                                        "Downloading: ${status.filename.take(15)}…"
                                        }
                                ElevatedFilterChip(
                                        selected = syncStatus is SyncViewModel.SyncStatus.Synced,
                                        onClick = {},
                                        label = { Text(chipLabel) }
                                )

                                val isSynced = syncStatus is SyncViewModel.SyncStatus.Synced
                                val glowColor = MaterialTheme.colorScheme.primary
                                Box(
                                        contentAlignment = Alignment.Center,
                                        modifier =
                                                Modifier.size(280.dp).pointerInput(durationMs) {
                                                        var accumulatedDrag = 0f
                                                        detectHorizontalDragGestures(
                                                                onDragStart = { dragOffset = 0f },
                                                                onDragEnd = {
                                                                        if (abs(accumulatedDrag) >
                                                                                        10f
                                                                        ) {
                                                                                val diffSec =
                                                                                        (accumulatedDrag /
                                                                                                        10f)
                                                                                                .toDouble()
                                                                                val target =
                                                                                        (playbackPos +
                                                                                                        diffSec)
                                                                                                .coerceIn(
                                                                                                        0.0,
                                                                                                        durationMs /
                                                                                                                1000.0
                                                                                                )
                                                                                vm.seek(target)
                                                                        }
                                                                        accumulatedDrag = 0f
                                                                        dragOffset = null
                                                                },
                                                                onDragCancel = {
                                                                        accumulatedDrag = 0f
                                                                        dragOffset = null
                                                                },
                                                                onHorizontalDrag = { _, dragAmount
                                                                        ->
                                                                        accumulatedDrag +=
                                                                                dragAmount
                                                                        dragOffset = accumulatedDrag
                                                                }
                                                        )
                                                }
                                ) {
                                        if (isSynced) {
                                                Box(
                                                        modifier =
                                                                Modifier.size(280.dp)
                                                                        .clip(CircleShape)
                                                                        .border(
                                                                                width =
                                                                                        glowRadius
                                                                                                .dp /
                                                                                                4,
                                                                                color =
                                                                                        glowColor
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                glowAlpha
                                                                                                ),
                                                                                shape = CircleShape
                                                                        )
                                                )
                                        }
                                        Box(
                                                modifier =
                                                        Modifier.size(240.dp)
                                                                .clip(RoundedCornerShape(20.dp))
                                                                .background(
                                                                        Brush.radialGradient(
                                                                                listOf(
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .primaryContainer,
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .secondaryContainer
                                                                                )
                                                                        )
                                                                ),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Icon(
                                                        Icons.Filled.MusicNote,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(80.dp),
                                                        tint =
                                                                MaterialTheme.colorScheme
                                                                        .onPrimaryContainer
                                                )
                                        }
                                }

                                val trackHash = roomState?.trackHash
                                val trackName = trackHash?.let { resolvedNames[it] ?: it }
                                Text(
                                        if (!trackName.isNullOrBlank()) trackName else "No track",
                                        style =
                                                MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.SemiBold
                                                ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                )

                                Slider(
                                        value = displayPos.toFloat(),
                                        onValueChange = { sliderPos = it },
                                        onValueChangeFinished = {
                                                sliderPos?.let { vm.seek(it.toDouble()) }
                                                sliderPos = null
                                        },
                                        valueRange = 0f..maxOf((durationMs / 1000f).toFloat(), 1f),
                                        modifier =
                                                Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                                )

                                Row(
                                        horizontalArrangement =
                                                Arrangement.spacedBy(
                                                        8.dp,
                                                        Alignment.CenterHorizontally
                                                ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                ) {
                                        FilledIconButton(
                                                onClick = { vm.queuePrev() },
                                                modifier = Modifier.size(56.dp)
                                        ) {
                                                Icon(
                                                        Icons.Filled.SkipPrevious,
                                                        contentDescription = "Previous",
                                                        modifier = Modifier.size(28.dp)
                                                )
                                        }
                                        FilledIconButton(
                                                onClick = {
                                                        if (isPlaying) vm.pause() else vm.play()
                                                },
                                                modifier = Modifier.size(72.dp),
                                                colors =
                                                        IconButtonDefaults.filledIconButtonColors(
                                                                containerColor =
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                        )
                                        ) {
                                                Icon(
                                                        if (isPlaying) Icons.Filled.Pause
                                                        else Icons.Filled.PlayArrow,
                                                        contentDescription =
                                                                if (isPlaying) "Pause" else "Play",
                                                        modifier = Modifier.size(36.dp)
                                                )
                                        }
                                        FilledIconButton(
                                                onClick = { vm.queueNext() },
                                                modifier = Modifier.size(56.dp)
                                        ) {
                                                Icon(
                                                        Icons.Filled.SkipNext,
                                                        contentDescription = "Next",
                                                        modifier = Modifier.size(28.dp)
                                                )
                                        }
                                }
                        }

                        AnimatedVisibility(
                                visible = trackChangedBy != null,
                                enter = slideInVertically { -it } + fadeIn(),
                                exit = slideOutVertically { -it } + fadeOut(),
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                        ) {
                                Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                ) {
                                        Text(
                                                "Track changed by ${trackChangedBy?.take(8) ?: ""}",
                                                modifier =
                                                        Modifier.padding(
                                                                horizontal = 16.dp,
                                                                vertical = 8.dp
                                                        ),
                                                style = MaterialTheme.typography.labelMedium
                                        )
                                }
                        }
                }
        }

        if (showQueue) {
                ModalBottomSheet(
                        onDismissRequest = { showQueue = false },
                        sheetState = queueSheetState
                ) {
                        Text(
                                "Queue",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                itemsIndexed(queue) { index, hash ->
                                        val isCurrentTrack = index == currentIndex
                                        val name = resolvedNames[hash] ?: hash
                                        ListItem(
                                                headlineContent = {
                                                        Text(
                                                                name,
                                                                fontWeight =
                                                                        if (isCurrentTrack)
                                                                                FontWeight.Bold
                                                                        else FontWeight.Normal,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                        )
                                                },
                                                leadingContent = {
                                                        Icon(
                                                                Icons.Filled.MusicNote,
                                                                contentDescription = null
                                                        )
                                                },
                                                supportingContent =
                                                        if (isCurrentTrack) {
                                                                {
                                                                        Text(
                                                                                "Playing",
                                                                                color =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .primary
                                                                        )
                                                                }
                                                        } else null,
                                                trailingContent = {
                                                        Row(
                                                                horizontalArrangement =
                                                                        Arrangement.spacedBy(8.dp),
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically
                                                        ) {
                                                                IconButton(
                                                                        onClick = {
                                                                                vm.queueRemove(
                                                                                        index
                                                                                )
                                                                        }
                                                                ) {
                                                                        Icon(
                                                                                Icons.Filled.Delete,
                                                                                contentDescription =
                                                                                        "Remove from Queue"
                                                                        )
                                                                }
                                                                Icon(
                                                                        Icons.Filled.DragHandle,
                                                                        contentDescription =
                                                                                "Reorder",
                                                                        modifier =
                                                                                Modifier
                                                                                        .pointerInput(
                                                                                                index
                                                                                        ) {
                                                                                                var dragAmountTotal =
                                                                                                        0f
                                                                                                detectVerticalDragGestures(
                                                                                                        onDragEnd = {
                                                                                                                dragAmountTotal =
                                                                                                                        0f
                                                                                                        }
                                                                                                ) {
                                                                                                        change,
                                                                                                        dragAmount
                                                                                                        ->
                                                                                                        change.consume()
                                                                                                        dragAmountTotal +=
                                                                                                                dragAmount
                                                                                                        if (dragAmountTotal >
                                                                                                                        100f &&
                                                                                                                        index <
                                                                                                                                queue.size -
                                                                                                                                        1
                                                                                                        ) {
                                                                                                                vm.queueMove(
                                                                                                                        index,
                                                                                                                        index +
                                                                                                                                1
                                                                                                                )
                                                                                                                dragAmountTotal =
                                                                                                                        0f
                                                                                                        } else if (dragAmountTotal <
                                                                                                                        -100f &&
                                                                                                                        index >
                                                                                                                                0
                                                                                                        ) {
                                                                                                                vm.queueMove(
                                                                                                                        index,
                                                                                                                        index -
                                                                                                                                1
                                                                                                                )
                                                                                                                dragAmountTotal =
                                                                                                                        0f
                                                                                                        }
                                                                                                }
                                                                                        }
                                                                )
                                                        }
                                                },
                                                colors =
                                                        if (isCurrentTrack)
                                                                ListItemDefaults.colors(
                                                                        containerColor =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primaryContainer
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.3f
                                                                                        )
                                                                )
                                                        else ListItemDefaults.colors(),
                                                modifier =
                                                        Modifier.clickable {
                                                                vm.queuePlayAt(index)
                                                                showQueue = false
                                                        }
                                                                .pointerInput(index) {
                                                                        detectHorizontalDragGestures {
                                                                                _,
                                                                                dragAmount ->
                                                                                if (dragAmount <
                                                                                                -50f
                                                                                )
                                                                                        vm.queueRemove(
                                                                                                index
                                                                                        )
                                                                        }
                                                                }
                                        )
                                }
                                item { Spacer(Modifier.height(32.dp)) }
                        }
                }
        }

        if (showMembers) {
                ModalBottomSheet(
                        onDismissRequest = { showMembers = false },
                        sheetState = membersSheetState
                ) {
                        Text(
                                "Members",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(members.entries.toList(), key = { it.key }) {
                                        (userId, username) ->
                                        ListItem(
                                                headlineContent = {
                                                        Text(
                                                                username,
                                                                fontWeight = FontWeight.Medium
                                                        )
                                                },
                                                supportingContent = {
                                                        if (userId == roomState?.ownerId)
                                                                Text(
                                                                        "Host",
                                                                        color = Color(0xFFFFD700)
                                                                )
                                                        else null
                                                },
                                                leadingContent = {
                                                        MemberAvatar(
                                                                displayName = username,
                                                                isSelf = userId == selfId
                                                        )
                                                }
                                        )
                                }
                                item { Spacer(Modifier.height(32.dp)) }
                        }
                }
        }

        if (showUpload) {
                var localFiles by remember {
                        mutableStateOf<List<com.tba5854.syncbeats.core.entity.SongEntity>>(
                                emptyList()
                        )
                }
                LaunchedEffect(Unit) {
                        withContext(Dispatchers.IO) { localFiles = MusicStorage.getAll() }
                }

                val importLauncher =
                        rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.OpenMultipleDocuments(),
                                onResult = { uris ->
                                        if (uris.isNotEmpty()) {
                                                scope.launch(Dispatchers.IO) {
                                                        uris.forEach { uri ->
                                                                runCatching {
                                                                        val entity =
                                                                                MusicStorage
                                                                                        .importUri(
                                                                                                context,
                                                                                                uri
                                                                                        )
                                                                        vm.queueAdd(entity.hash)
                                                                        if (roomState?.trackHash
                                                                                        .isNullOrBlank()
                                                                        ) {
                                                                                vm.setTrack(
                                                                                        entity.hash
                                                                                )
                                                                        }
                                                                }
                                                        }
                                                        withContext(Dispatchers.Main) {
                                                                showUpload = false
                                                        }
                                                }
                                        }
                                }
                        )

                ModalBottomSheet(
                        onDismissRequest = { showUpload = false },
                        sheetState = uploadSheetState
                ) {
                        Text(
                                "Add to Room",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        ListItem(
                                headlineContent = {
                                        Text("Import from Storage", fontWeight = FontWeight.Medium)
                                },
                                leadingContent = {
                                        Icon(
                                                Icons.Filled.Folder,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                        )
                                },
                                modifier =
                                        Modifier.clickable {
                                                importLauncher.launch(arrayOf("audio/*"))
                                        }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Text(
                                "Select from Library",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.primary
                        )

                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(localFiles, key = { it.hash }) { song ->
                                        ListItem(
                                                headlineContent = {
                                                        Text(
                                                                song.title,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                        )
                                                },
                                                leadingContent = {
                                                        Icon(
                                                                Icons.Filled.MusicNote,
                                                                contentDescription = null
                                                        )
                                                },
                                                trailingContent = {
                                                        Row(
                                                                horizontalArrangement =
                                                                        Arrangement.spacedBy(8.dp)
                                                        ) {
                                                                IconButton(
                                                                        onClick = {
                                                                                vm.queueAdd(
                                                                                        song.hash
                                                                                )
                                                                                showUpload = false
                                                                        }
                                                                ) {
                                                                        Icon(
                                                                                Icons.AutoMirrored
                                                                                        .Filled
                                                                                        .QueueMusic,
                                                                                contentDescription =
                                                                                        "Add to Queue",
                                                                                tint =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .primary
                                                                        )
                                                                }
                                                                FilledIconButton(
                                                                        onClick = {
                                                                                vm.queueAdd(
                                                                                        song.hash
                                                                                )
                                                                                vm.setTrack(
                                                                                        song.hash
                                                                                )
                                                                                showUpload = false
                                                                        }
                                                                ) {
                                                                        Icon(
                                                                                Icons.Filled
                                                                                        .PlayArrow,
                                                                                contentDescription =
                                                                                        "Play Now",
                                                                        )
                                                                }
                                                        }
                                                },
                                        )
                                }
                                if (localFiles.isEmpty()) {
                                        item {
                                                Text(
                                                        "Library is empty.",
                                                        modifier = Modifier.padding(16.dp),
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                        }
                                }
                                item { Spacer(Modifier.height(32.dp)) }
                        }
                }
        }
}

@Composable
private fun MemberAvatar(displayName: String, isSelf: Boolean) {
        val initials =
                displayName
                        .trim()
                        .split(" ")
                        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                        .take(2)
                        .joinToString("")
                        .ifEmpty { displayName.take(2).uppercase() }

        val borderMod =
                if (isSelf) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val borderAlpha by
                                infiniteTransition.animateFloat(
                                        initialValue = 0.4f,
                                        targetValue = 1f,
                                        animationSpec =
                                                infiniteRepeatable(
                                                        animation = tween(800),
                                                        repeatMode = RepeatMode.Reverse
                                                ),
                                        label = "borderAlpha"
                                )
                        Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = borderAlpha),
                                CircleShape
                        )
                } else Modifier

        Box(
                modifier =
                        Modifier.size(40.dp)
                                .then(borderMod)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
        ) {
                Text(
                        initials,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                )
        }
}
