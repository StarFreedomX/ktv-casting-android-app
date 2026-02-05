package zju.bangdream.ktv.casting

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val wifiManager = getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager
        val multicastLock = wifiManager.createMulticastLock("ktv_search_lock")
        multicastLock.acquire()

        RustEngine.initLogging(2)
        val popipaPink = Color(0xFFFF3377)
        setContent {
            // 自定义 MaterialTheme 的配色方案
            val customColorScheme = lightColorScheme(
                primary = popipaPink,
                onPrimary = Color.White,
                primaryContainer = popipaPink.copy(alpha = 0.1f),
                onPrimaryContainer = popipaPink,
                secondary = popipaPink,
                tertiary = Color(0xFF555555) // 用于“播放”按钮未激活时的灰色
            )

            MaterialTheme(colorScheme = customColorScheme) {
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

@Composable
fun CastingControlScreen(device: DlnaDeviceItem, onReset: () -> Unit) {
    val progressState by CastingService.playbackProgress.collectAsState()
    val (currentSec, totalSec) = progressState

    // 状态维护：当前是否正在播放（初始假设为播放，后续由接口同步）
    var isPlaying by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "正在投屏中", style = MaterialTheme.typography.titleSmall, color = Color.Gray)
        Text(text = device.name, style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(48.dp))

        // 进度条
        val progress = if (totalSec > 0) currentSec.toFloat() / totalSec.toFloat() else 0f
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(currentSec))
            Text(formatTime(totalSec))
        }

        Spacer(modifier = Modifier.height(48.dp))

        // --- 控制按钮区 ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 播放/暂停切换
            Button(
                onClick = {
                    val result = RustEngine.togglePause()
                    isPlaying = (result == 1)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                )
            ) {
                Text(if (isPlaying) "暂停" else "播放")
            }

            // 下一首
            Button(
                onClick = { RustEngine.nextSong() },
                modifier = Modifier.weight(1f)
            ) {
                Text("下一首")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 音量控制区 ---
        VolumeControlGroup()

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedButton(
            onClick = {
                CastingService.resetProgress()
                onReset()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("更换设备 / 停止投屏")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeControlGroup() {
    var volumeValue by remember { mutableIntStateOf(50) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        thread {
            val remoteVol = RustEngine.getVolume()
            if (remoteVol != -1) { volumeValue = remoteVol }
        }
    }

    val commitVolume = { newValue: Int ->
        val target = newValue.coerceIn(0, 100)
        volumeValue = target
        thread { RustEngine.setVolume(target) }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        // 动态显示数字：只有在滑动时或者点击按钮时显现一秒（这里演示滑动实时显示）
        Row(
            modifier = Modifier.fillMaxWidth()
            .height(24.dp)
            .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(text = "设备音量", style = MaterialTheme.typography.labelMedium, color = Color.Gray)

            // 如果正在拖动，显示当前数值
            if (isDragging) {
                Text(
                    text = "$volumeValue",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 48.dp) // 大致对齐滑块区域
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = { commitVolume(volumeValue - 5) }) {
                Text("-", style = MaterialTheme.typography.titleMedium)
            }

            Slider(
                value = volumeValue.toFloat(),
                onValueChange = {
                    isDragging = true // 开始滑动
                    volumeValue = it.toInt()
                },
                onValueChangeFinished = {
                    isDragging = false // 停止滑动
                    commitVolume(volumeValue)
                },
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f),
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = remember { MutableInteractionSource() },
                        thumbSize = DpSize(14.dp, 14.dp),
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(2.dp),
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            )

            IconButton(onClick = { commitVolume(volumeValue + 5) }) {
                Text("+", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun DeviceSelectorScreen(onDeviceSelect: (String, Long, DlnaDeviceItem) -> Unit) {
    // 状态：网址、房间号、设备列表
    var baseUrl by remember { mutableStateOf("https://ktv.starfreedomx.top") }
    var roomIdStr by remember { mutableStateOf("1111") }
    var deviceList by remember { mutableStateOf(emptyArray<DlnaDeviceItem>()) }
    var isSearching by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Text(text = "KTV 投屏助手", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // --- 配置区 ---
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

        // --- 搜索区 ---
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

        // --- 结果区 ---
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
// 格式化辅助函数
fun formatTime(seconds: Long): String {
    if (seconds < 0) return "00:00"
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}