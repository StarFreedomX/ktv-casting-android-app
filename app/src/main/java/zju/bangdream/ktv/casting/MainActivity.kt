package zju.bangdream.ktv.casting

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    // 注意：不再需要实例化 RustEngine()，直接使用类名即可

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val wifiManager = getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager
        val multicastLock = wifiManager.createMulticastLock("ktv_search_lock")
        multicastLock.acquire()

        // 调用静态方法初始化日志
        RustEngine.initLogging(2)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // 传入 context 以便启动 Service
                    DeviceSelectorScreen(onDeviceSelect = { device ->
                        startCastingService(device)
                    })
                }
            }
        }
    }

    private fun startCastingService(device: DlnaDeviceItem) {
        val intent = Intent(this, CastingService::class.java).apply {
            putExtra("base_url", "http://192.168.1.2:5526")
            putExtra("room_id", 1111L)
            putExtra("location", device.location)
        }

        // 启动前台服务，确保后台不掉线
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

@Composable
fun DeviceSelectorScreen(onDeviceSelect: (DlnaDeviceItem) -> Unit) {
    var deviceList by remember { mutableStateOf(emptyArray<DlnaDeviceItem>()) }
    var isSearching by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "KTV 投屏助手", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                isSearching = true
                thread {
                    // 使用静态方法 searchDevices
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

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(deviceList) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            android.util.Log.i("KTV_UI", "选择设备: ${device.name}")
                            // 回调给 Activity 处理 Service 启动
                            onDeviceSelect(device)
                        },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = device.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = device.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}