package zju.bangdream.ktv.casting.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zju.bangdream.ktv.casting.DlnaDeviceItem
import zju.bangdream.ktv.casting.RustEngine
import kotlin.concurrent.thread

@Composable
fun DeviceSelectorScreen(onDeviceSelect: (String, Long, DlnaDeviceItem) -> Unit) {
    var baseUrl by remember { mutableStateOf("https://ktv.starfreedomx.top") }
    var roomIdStr by remember { mutableStateOf("1111") }
    var deviceList by remember { mutableStateOf(emptyArray<DlnaDeviceItem>()) }
    var isSearching by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "KTV 投屏助手", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

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