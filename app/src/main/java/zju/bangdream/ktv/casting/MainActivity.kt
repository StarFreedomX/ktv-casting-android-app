package zju.bangdream.ktv.casting

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
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
                            if (device == null) emptyMap()
                            else mapOf("name" to device.name, "location" to device.location)
                        },
                        restore = { data ->
                            val name = data["name"]
                            val location = data["location"]
                            if (name != null && location != null) DlnaDeviceItem(name, location)
                            else null
                        }
                    )
                }

                var selectedDevice by rememberSaveable(stateSaver = deviceSaver) {
                    mutableStateOf<DlnaDeviceItem?>(null)
                }
                var selectedRoomId by rememberSaveable { mutableLongStateOf(0L) }
                var selectedBaseUrl by rememberSaveable { mutableStateOf("") }
                
                var showLogs by rememberSaveable { mutableStateOf(false) }

                // PagerState 管理 3 个页面，beyondViewportPageCount 确保它们始终存活
                val pagerState = rememberPagerState(pageCount = { 3 })
                val scope = rememberCoroutineScope()

                val prefs = remember { getSharedPreferences("ktv_settings", Context.MODE_PRIVATE) }

                Surface(
                    modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 底层主界面 Scaffold 始终处于 Composition 中，避免状态丢失
                        Scaffold(
                            bottomBar = {
                                NavigationBar {
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Default.Search, contentDescription = "连接") },
                                        label = { Text("连接") },
                                        selected = pagerState.currentPage == 0,
                                        onClick = { scope.launch { pagerState.scrollToPage(0) } }
                                    )
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = "控制") },
                                        label = { Text("控制") },
                                        selected = pagerState.currentPage == 1,
                                        onClick = { scope.launch { pagerState.scrollToPage(1) } }
                                    )
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                                        label = { Text("设置") },
                                        selected = pagerState.currentPage == 2,
                                        onClick = { scope.launch { pagerState.scrollToPage(2) } }
                                    )
                                }
                            }
                        ) { padding ->
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.padding(padding),
                                userScrollEnabled = false,
                                beyondViewportPageCount = 2 // 关键：即便不在当前页，也会保留 Compose 状态
                            ) { pageIndex ->
                                when (pageIndex) {
                                    0 -> DeviceSelectorScreen(onDeviceSelect = { url, room, device ->
                                        // 修复：如果当前已经有连接，先停止旧连接，防止 Rust 引擎初始化冲突
                                        if (selectedDevice != null) {
                                            stopCasting()
                                        }
                                        selectedDevice = device
                                        selectedRoomId = room
                                        selectedBaseUrl = url
                                        startCastingService(url, room, device)
                                        scope.launch { pagerState.animateScrollToPage(1) }
                                    })
                                    1 -> {
                                        if (selectedDevice != null) {
                                            CastingControlScreen(
                                                device = selectedDevice!!,
                                                roomId = selectedRoomId,
                                                baseUrl = selectedBaseUrl,
                                                onStop = {
                                                    stopCasting()
                                                    selectedDevice = null
                                                    selectedRoomId = 0L
                                                    selectedBaseUrl = ""
                                                },
                                                onChangeSettings = { newUrl, newRoomId ->
                                                    stopCasting()
                                                    selectedBaseUrl = newUrl
                                                    selectedRoomId = newRoomId
                                                    prefs.edit().apply {
                                                        putString("base_url", newUrl)
                                                        putString("room_id", newRoomId.toString())
                                                        apply()
                                                    }
                                                    selectedDevice?.let { startCastingService(newUrl, newRoomId, it) }
                                                },
                                                onChangeDevice = { newDevice ->
                                                    stopCasting()
                                                    selectedDevice = newDevice
                                                    startCastingService(selectedBaseUrl, selectedRoomId, newDevice)
                                                }
                                            )
                                        } else {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text("请先在“连接”页面选择投屏设备", style = MaterialTheme.typography.bodyLarge)
                                            }
                                        }
                                    }
                                    2 -> SettingsScreen(
                                        onBack = { scope.launch { pagerState.animateScrollToPage(0) } },
                                        onOpenLogs = { showLogs = true }
                                    )
                                }
                            }
                        }

                        // 日志页作为覆盖层，不销毁 Scaffold，确保切换回来时状态依然在
                        if (showLogs) {
                            BackHandler { showLogs = false }
                            LogScreen(onBack = { showLogs = false })
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

    private fun stopCasting() {
        stopService(Intent(this, CastingService::class.java))
        RustEngine.resetEngine()
        CastingService.resetProgress()
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
