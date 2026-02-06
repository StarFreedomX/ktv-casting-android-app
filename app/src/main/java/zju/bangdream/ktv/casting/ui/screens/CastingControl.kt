package zju.bangdream.ktv.casting.ui.screens

import android.util.Log
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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

/**
 * 有状态组件 (Stateful)：负责从 Service 获取实时数据，并处理与 Rust 层的交互
 */
@Composable
fun CastingControlScreen(
    device: DlnaDeviceItem,
    onReset: () -> Unit
) {
    // 观察来自 Service 的播放进度流
    val progressState by CastingService.playbackProgress.collectAsState()
    val (currentSec, totalSec) = progressState

    // 播放/暂停状态
    var isPlaying by remember { mutableStateOf(true) }

    // 调用纯 UI 展示组件
    CastingControlContent(
        deviceName = device.name,
        currentSec = currentSec,
        totalSec = totalSec,
        isPlaying = isPlaying,
        onTogglePause = {
            val result = RustEngine.togglePause()
            isPlaying = (result == 1)
        },
        onNext = {
            RustEngine.nextSong()
        },
        onSeek = { target ->
            // 在后台线程执行网络 IO 密集型操作
            thread {
                val res = RustEngine.jumpToSecs(target)
                Log.d("CastingControl", "Seek result: $res at ${target}s")
            }
        },
        onReset = {
            CastingService.resetProgress()
            onReset()
        }
    )
}

/**
 * 无状态组件 (Stateless)：负责 UI 布局。
 * 不直接依赖 RustEngine 或 Service，非常适合进行预览。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastingControlContent(
    deviceName: String,
    currentSec: Long,
    totalSec: Long,
    isPlaying: Boolean,
    onTogglePause: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Int) -> Unit,
    onReset: () -> Unit
) {
    // 处理进度条拖动时的本地临时状态，防止进度回跳
    var isDraggingProgress by remember { mutableStateOf(false) }
    var dragProgressValue by remember { mutableFloatStateOf(0f) }

    val displaySec = if (isDraggingProgress) dragProgressValue.toLong() else currentSec
    val totalProgress = if (totalSec > 0) totalSec.toFloat() else 100f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 头部信息
        Text(text = "正在投屏中", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Text(text = deviceName, style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(48.dp))

        // --- 进度控制区 ---
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
            modifier = Modifier
                .fillMaxWidth(),
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = remember { MutableInteractionSource() },
                    modifier = Modifier
                        .size(10.dp) // 声明尺寸
                        .offset(y = 2.5.dp), // 如果还有极小偏差，用 offset 比 padding 更专业
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

        // --- 主控按钮区 ---
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

        // --- 音量控制区 (引入外部组件) ---
        VolumeControlGroup()

        Spacer(modifier = Modifier.height(56.dp))

        // 退出/重置
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("更换设备 / 停止投屏")
        }
    }
}

/**
 * 时间格式化工具 (00:00)
 */
private fun formatTime(seconds: Long): String {
    if (seconds < 0) return "00:00"
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

/**
 * Android Studio 预览专用函数
 */
@Preview(showBackground = true, name = "Casting Control - Normal")
@Composable
fun CastingControlPreview() {
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFFFF3377))) {
        CastingControlContent(
            deviceName = "Preview Device",
            currentSec = 45,
            totalSec = 210,
            isPlaying = true,
            onTogglePause = {},
            onNext = {},
            onSeek = {},
            onReset = {}
        )
    }
}