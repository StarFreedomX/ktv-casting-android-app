package zju.bangdream.ktv.casting.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import zju.bangdream.ktv.casting.DlnaDeviceItem
import zju.bangdream.ktv.casting.RustEngine
import kotlin.concurrent.thread

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSelectorScreen(onDeviceSelect: (String, Long, DlnaDeviceItem) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ktv_settings", Context.MODE_PRIVATE) }

    var baseUrl by remember {
        mutableStateOf(prefs.getString("base_url", "https://ktv.starfreedomx.top") ?: "")
    }
    var roomIdStr by remember {
        mutableStateOf(prefs.getString("room_id", "1111") ?: "")
    }

    var deviceList by remember { mutableStateOf(emptyArray<DlnaDeviceItem>()) }
    var isSearching by remember { mutableStateOf(false) }

    val saveSettings = {
        prefs.edit().apply {
            putString("base_url", baseUrl)
            putString("room_id", roomIdStr)
            apply()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("连接设备") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("服务器网址") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = roomIdStr,
                onValueChange = { roomIdStr = it },
                label = { Text("房间号") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    saveSettings()
                    isSearching = true
                    thread {
                        val results = RustEngine.searchDevices()
                        deviceList = results
                        isSearching = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSearching
            ) {
                Text(if (isSearching) "正在搜索..." else "搜索可用设备")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "可用设备 (${deviceList.size}):", style = MaterialTheme.typography.titleMedium)
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(deviceList) { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                saveSettings()
                                val roomId = roomIdStr.toLongOrNull() ?: 0L
                                onDeviceSelect(baseUrl, roomId, device)
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = device.name, style = MaterialTheme.typography.bodyLarge)
                            Text(text = device.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
    }
}
