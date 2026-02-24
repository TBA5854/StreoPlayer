package com.tba5854.syncbeats.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tba5854.syncbeats.core.entity.SongEntity
import com.tba5854.syncbeats.core.storage.MusicStorage
import com.tba5854.syncbeats.ui.viewmodel.SyncViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(vm: SyncViewModel, onOpenPlayer: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var songs by remember { mutableStateOf(MusicStorage.getAll()) }
    var isImporting by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf("") }

    val launcher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments(),
                    onResult = { uris ->
                        if (uris.isNotEmpty()) {
                            isImporting = true
                            scope.launch(Dispatchers.IO) {
                                var imported = 0
                                var skipped = 0
                                uris.forEach { uri ->
                                    runCatching { MusicStorage.importUri(context, uri) }.onSuccess {
                                        if (it.isDuplicate) skipped++ else imported++
                                    }
                                }
                                val all = MusicStorage.getAll()
                                withContext(Dispatchers.Main) {
                                    songs = all
                                    importMessage =
                                            "Imported $imported${if (skipped > 0) ", $skipped skipped" else ""}"
                                    isImporting = false
                                }
                            }
                        }
                    }
            )

    Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                        title = {
                            Text(
                                    "Library",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                            )
                        },
                        actions = {
                            IconButton(onClick = { launcher.launch(arrayOf("audio/*")) }) {
                                if (isImporting) {
                                    CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onSurface
                                    )
                                } else {
                                    Icon(Icons.Filled.Add, contentDescription = "Add Music")
                                }
                            }
                            IconButton(onClick = onSettings) {
                                Icon(Icons.Filled.Settings, contentDescription = "Settings")
                            }
                        },
                        colors =
                                TopAppBarDefaults.centerAlignedTopAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                )
                )
            },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(
                    visible = importMessage.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
            ) {
                Card(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                MaterialTheme.colorScheme.secondaryContainer
                                )
                ) {
                    Text(
                            importMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (songs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text(
                                "No music yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                                "Tap + Add Music to import",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            "${songs.size} song${if (songs.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(songs, key = { it.hash }) { song ->
                        SongListItem(
                                song = song,
                                onPlay = {
                                    vm.playSolo(song.hash)
                                    onOpenPlayer()
                                },
                                onDelete = {
                                    MusicStorage.delete(song.hash)
                                    songs = MusicStorage.getAll()
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SongListItem(song: SongEntity, onPlay: () -> Unit, onDelete: () -> Unit) {
    ListItem(
            headlineContent = {
                Text(
                        song.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                )
            },
            supportingContent = {
                Text(
                        song.hash.take(12).lowercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                )
            },
            leadingContent = {
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            trailingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledIconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) {
                        Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
}
