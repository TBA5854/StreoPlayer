package com.tba5854.stereo_player.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tba5854.stereo_player.core.storage.MusicStorage

@Composable
fun MusicImportScreen() {
    val context = LocalContext.current
    var songs by remember { mutableStateOf(MusicStorage.getAll()) }
    var message by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }

    val launcher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments(),
                    onResult = { uris ->
                        if (uris.isNotEmpty()) {
                            isImporting = true
                            var importedCount = 0
                            var duplicateCount = 0

                            // Process imports (Note: in a real app this should be in a
                            // Coroutine/ViewModel)
                            uris.forEach { uri ->
                                try {
                                    val result = MusicStorage.importUri(context, uri)
                                    if (result.isDuplicate) duplicateCount++ else importedCount++
                                } catch (e: Exception) {
                                    android.util.Log.e("Import", "Failed to import $uri", e)
                                }
                            }

                            songs = MusicStorage.getAll()
                            message = "Imported $importedCount songs ($duplicateCount skipped)"
                            isImporting = false
                        }
                    }
            )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Music Library", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        Button(
                onClick = { launcher.launch(arrayOf("audio/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isImporting
        ) {
            if (isImporting) {
                CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Importing...")
            } else {
                Text("Pick Music Files")
            }
        }

        if (message.isNotEmpty()) {
            Text(
                    message,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Stored Songs (${songs.size})", style = MaterialTheme.typography.titleMedium)

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(songs) { song ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(song.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                                "MD5: ${song.hash.lowercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                        )

                        Row(modifier = Modifier.padding(top = 8.dp)) {
                            FilledTonalButton(
                                    onClick = {
                                        MusicStorage.delete(song.hash)
                                        songs = MusicStorage.getAll()
                                    },
                                    contentPadding =
                                            PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                            ) { Text("Delete", style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }
        }
    }
}
