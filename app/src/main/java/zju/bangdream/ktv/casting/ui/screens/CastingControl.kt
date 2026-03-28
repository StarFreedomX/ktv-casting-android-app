package zju.bangdream.ktv.casting.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import zju.bangdream.ktv.casting.CastingService
import zju.bangdream.ktv.casting.DlnaDeviceItem
import zju.bangdream.ktv.casting.RustEngine
import zju.bangdream.ktv.casting.ui.components.VolumeControlGroup
import kotlin.concurrent.thread

@Composable
fun CastingControlScreen(
    device: DlnaDeviceItem,
    roomId: Long,
    baseUrl: String,
    onStop: () -> Unit,
    onChangeSettings: (String, Long) -> Unit,
    onChangeDevice: (DlnaDeviceItem) -> Unit
) {
    val progressState by CastingService.playbackProgress.collectAsState()
    val (currentSec, totalSec) = progressState
    val songTitle by CastingService.currentSongTitle.collectAsState()

    var isPlaying by remember { mutableStateOf(true) }

    CastingControlContent(
        deviceName = device.name,
        roomId = roomId,
        baseUrl = baseUrl,
        songTitle = songTitle,
        currentSec = currentSec,
        totalSec = totalSec,
        isPlaying = isPlaying,
        onTogglePause = {
            val result = RustEngine.togglePause()
            isPlaying = (result == 1)
        },
        onNext = { RustEngine.nextSong() },
        onSeek = { target ->
            thread { RustEngine.jumpToSecs(target) }
        },
        onStop = onStop,
        onChangeSettings = onChangeSettings,
        onChangeDevice = onChangeDevice
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastingControlContent(
    deviceName: String,
    roomId: Long,
    baseUrl: String,
    songTitle: String,
    currentSec: Long,
    totalSec: Long,
    isPlaying: Boolean,
    onTogglePause: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Int) -> Unit,
    onStop: () -> Unit,
    onChangeSettings: (String, Long) -> Unit,
    onChangeDevice: (DlnaDeviceItem) -> Unit
) {
    var isDraggingProgress by remember { mutableStateOf(false) }
    var dragProgressValue by remember { mutableFloatStateOf(0f) }
    var showStopDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    
    val displaySec = if (isDraggingProgress) dragProgressValue.toLong() else currentSec
    val totalProgress = if (totalSec > 0) totalSec.toFloat() else 100f

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text(text = "停止投屏") },
            text = { Text(text = "确定要停止当前投屏吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStopDialog = false
                        onStop()
                    }
                ) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showSettingsDialog) {
        var newBaseUrl by remember { mutableStateOf(baseUrl) }
        var newRoomId by remember { mutableStateOf(roomId.toString()) }
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("更换房间设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newBaseUrl,
                        onValueChange = { newBaseUrl = it },
                        label = { Text("服务器网址") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = newRoomId,
                        onValueChange = { newRoomId = it },
                        label = { Text("房间号") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = newRoomId.toLongOrNull()
                    if (id != null) {
                        showSettingsDialog = false
                        onChangeSettings(newBaseUrl, id)
                    }
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showDeviceDialog) {
        var deviceList by remember { mutableStateOf(emptyArray<DlnaDeviceItem>()) }
        var isSearching by remember { mutableStateOf(false) }
        var hasSearched by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showDeviceDialog = false },
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("更换投屏设备")
                    if (isSearching) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
            },
            text = {
                Column {
                    Button(
                        onClick = {
                            isSearching = true
                            hasSearched = true
                            thread {
                                val results = RustEngine.searchDevices()
                                deviceList = results
                                isSearching = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSearching,
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isSearching) "正在搜索..." else "搜索可用设备")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(modifier = Modifier.heightIn(max = 240.dp)) {
                        if (deviceList.isEmpty() && !isSearching && hasSearched) {
                            Text("未找到可用设备", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                        } else if (!hasSearched) {
                            Text("请点击上方按钮开始搜索设备", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(deviceList) { device ->
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showDeviceDialog = false
                                                onChangeDevice(device)
                                            }
                                    ) {
                                        ListItem(
                                            headlineContent = { Text(device.name) },
                                            supportingContent = { Text(device.location) },
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDeviceDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("正在播放") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(text = "正在投屏至", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFFEEEEEE),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { showDeviceDialog = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = deviceName,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.DarkGray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "修改设备",
                            modifier = Modifier.size(12.dp),
                            tint = Color.DarkGray
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { showSettingsDialog = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "房间: $roomId",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "修改房间",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = songTitle,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(48.dp))

            Slider(
                value = displaySec.toFloat().coerceIn(0f, totalProgress),
                onValueChange = {
                    isDraggingProgress = true
                    dragProgressValue = it
                },
                onValueChangeFinished = {
                    onSeek(dragProgressValue.toInt())
                    isDraggingProgress = false
                },
                valueRange = 0f..totalProgress,
                modifier = Modifier.fillMaxWidth(),
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = remember { MutableInteractionSource() },
                        modifier = Modifier
                            .size(10.dp)
                            .offset(y = 2.5.dp),
                        thumbSize = DpSize(10.dp, 10.dp),
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(sliderState = sliderState, modifier = Modifier.height(4.dp), drawStopIndicator = null)
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = formatTime(displaySec), style = MaterialTheme.typography.bodySmall)
                Text(text = formatTime(totalSec), style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(48.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onTogglePause,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaying) MaterialTheme.colorScheme.primary else Color(0xFF555555)
                    )
                ) {
                    Text(if (isPlaying) "暂停" else "播放")
                }

                Button(
                    onClick = onNext,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("下一首")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            VolumeControlGroup()

            Spacer(modifier = Modifier.weight(1f))

            OutlinedButton(
                onClick = { showStopDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("停止投屏")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun formatTime(seconds: Long): String {
    if (seconds < 0) return "00:00"
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
