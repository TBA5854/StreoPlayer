package com.tba5854.syncbeats.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tba5854.syncbeats.settings.Settings
import com.tba5854.syncbeats.ui.viewmodel.SyncViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
        vm: SyncViewModel,
        onBack: () -> Unit
) {
    var username by remember { mutableStateOf(Settings.config.userName) }
    var serverUrl by remember { mutableStateOf(Settings.config.serverIp) }
    var debugMode by remember { mutableStateOf(false) }

    Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                )
                            }
                        },
                        title = { Text("Settings") }
                )
            }
    ) { padding ->
        Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                    "Profile",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Text(
                    "Server",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("http://10.0.0.1:3000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            Button(
                    onClick = {
                        Settings.update {
                            it.userName = username.trim()
                            it.serverIp = serverUrl.trim()
                        }
                        vm.updateServerIp(serverUrl.trim())
                    },
                    modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply")
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                    onClick = { vm.resync() },
                    modifier = Modifier.fillMaxWidth()
            ) {
                Text("Resync")
            }

            Spacer(Modifier.height(24.dp))

            Text(
                    "Debug",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ListItem(
                    headlineContent = { Text("Debug mode") },
                    supportingContent = { Text("Show extra sync diagnostics") },
                    trailingContent = {
                        Switch(
                                checked = debugMode,
                                onCheckedChange = { debugMode = it }
                        )
                    }
            )
        }
    }
}
