package zju.bangdream.ktv.casting

import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zju.bangdream.ktv.casting.ui.screens.CastingControlScreen
import zju.bangdream.ktv.casting.ui.screens.DeviceSelectorScreen
import zju.bangdream.ktv.casting.ui.screens.LogScreen
import zju.bangdream.ktv.casting.ui.screens.SettingsScreen
import zju.bangdream.ktv.casting.ui.theme.KtvCastingTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemRequirements()
        RustEngine.initLogging(2)
    RustEngine.logFromKotlin("MainActivity", "应用启动", LogLevel.INFO)

        setContent {
            KtvCastingTheme {
                val deviceSaver = remember {
                    Saver<DlnaDeviceItem?, Map<String, String>>(
                        save = { device ->
                            if (device == null) {
                                emptyMap()
                            } else {
                                mapOf("name" to device.name, "location" to device.location)
                            }
                        },
                        restore = { data ->
                            val name = data["name"]
                            val location = data["location"]
                            if (name != null && location != null) {
                                DlnaDeviceItem(name, location)
                            } else {
                                null
                            }
                        }
                    )
                }

                var selectedDevice by rememberSaveable(stateSaver = deviceSaver) {
                    mutableStateOf<DlnaDeviceItem?>(null)
                }
                var selectedRoomId by rememberSaveable { mutableStateOf(0L) }
                var selectedBaseUrl by rememberSaveable { mutableStateOf("") }
                // 添加导航状态
                var currentScreen by rememberSaveable { mutableStateOf("main") }

                Surface(
                    modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (currentScreen == "settings") {
                        SettingsScreen(
                            onBack = { currentScreen = "main" },
                            onOpenLogs = { currentScreen = "logs" }
                        )
                    } else if (currentScreen == "logs") {
                        LogScreen(onBack = { currentScreen = "settings" })
                    } else {
                        Box {
                            // 原有的主逻辑
                            if (selectedDevice == null) {
                                DeviceSelectorScreen(onDeviceSelect = { url, room, device ->
                                    selectedDevice = device
                                    selectedRoomId = room
                                    selectedBaseUrl = url
                                    startCastingService(url, room, device)
                                })
                            } else {
                                CastingControlScreen(
                                    device = selectedDevice!!,
                                    roomId = selectedRoomId,
                                    onReset = {
                                        stopService(Intent(this@MainActivity, CastingService::class.java))
                                        RustEngine.resetEngine()
                                        selectedDevice = null
                                        selectedRoomId = 0L
                                        selectedBaseUrl = ""
                                    }
                                )
                            }


                            IconButton(
                                onClick = { currentScreen = "settings" },
                                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "设置")
                            }

                        }
                    }
                }
            }
        }
    }

    private fun setupSystemRequirements() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val wifiManager = getSystemService(WIFI_SERVICE) as WifiManager
        val multicastLock = wifiManager.createMulticastLock("ktv_search_lock")
        multicastLock.acquire()
    }

    private fun startCastingService(url: String, room: Long, device: DlnaDeviceItem) {
        RustEngine.logFromKotlin("Casting", "启动投屏服务: $url, room=$room")
        val intent = Intent(this, CastingService::class.java).apply {
            putExtra("base_url", url)
            putExtra("room_id", room)
            putExtra("location", device.location)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}