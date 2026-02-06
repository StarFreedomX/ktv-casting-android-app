package zju.bangdream.ktv.casting

import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import zju.bangdream.ktv.casting.ui.screens.CastingControlScreen
import zju.bangdream.ktv.casting.ui.screens.DeviceSelectorScreen
import zju.bangdream.ktv.casting.ui.theme.KtvCastingTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 初始化系统锁与权限
        setupSystemRequirements()
        RustEngine.initLogging(2)

        setContent {
            // 使用抽离出来的主题
            KtvCastingTheme {
                var selectedDevice by remember { mutableStateOf<DlnaDeviceItem?>(null) }

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (selectedDevice == null) {
                        DeviceSelectorScreen(onDeviceSelect = { url, room, device ->
                            selectedDevice = device
                            startCastingService(url, room, device)
                        })
                    } else {
                        CastingControlScreen(
                            device = selectedDevice!!,
                            onReset = {
                                stopService(Intent(this, CastingService::class.java))
                                RustEngine.resetEngine()
                                selectedDevice = null
                            }
                        )
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