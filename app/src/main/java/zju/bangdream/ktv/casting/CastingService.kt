package zju.bangdream.ktv.casting

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CastingService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null

    companion object {
        // 使用 StateFlow 存储进度状态
        private val _playbackProgress = MutableStateFlow(Pair(0L, 0L))
        val playbackProgress = _playbackProgress.asStateFlow()

        // --- 新增：重置进度状态的方法 ---
        fun resetProgress() {
            _playbackProgress.value = Pair(0L, 0L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val baseUrl = intent?.getStringExtra("base_url") ?: ""
        val roomId = intent?.getLongExtra("room_id", 0L) ?: 0L
        val location = intent?.getStringExtra("location") ?: ""

        // 1. 准备通知栏
        val notification = createNotification("准备投屏...")

        // 根据 Android 版本启动前台服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(1, notification)
        }

        // 2. 初始化 Rust 引擎
        RustEngine.startEngine(baseUrl, roomId, location)

        // 3. 开启轮询逻辑
        startCommanderLoop()

        return START_STICKY
    }

    private fun startCommanderLoop() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            Log.i("KTV_SERVICE", "loop start")
            delay(2000)
            while (isActive) {
                val current = RustEngine.queryProgress()
                val total = RustEngine.queryTotalDuration()

                // --- 修改：更新状态流，UI 侧 collectAsState 会感知到 ---
                _playbackProgress.value = Pair(current, total)

                if (current >= 0 && total > 0) {
                    // --- 修改：更新通知栏显示进度 (使用格式化后的时间) ---
                    updateNotification("正在播放: ${formatTime(current)} / ${formatTime(total)}")

                    // --- 核心控制逻辑 ---
                    if (total - current <= 2 && current > 5) {
                        Log.i("KTV_CMD", "距离结束仅剩 ${total - current}s，下达切歌指令")
                        RustEngine.nextSong()

                        // 切歌后强制等待 5 秒，防止重复触发
                        delay(5000)
                    }
                }

                delay(1000) // 每秒轮询一次
            }
        }
    }

    // --- 新增：时间格式化辅助函数 ---
    private fun formatTime(seconds: Long): String {
        if (seconds < 0) return "00:00"
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }

    // --- 通知栏相关逻辑 ---
    private fun createNotification(content: String): Notification {
        val channelId = "CastingChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "KTV Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("KTV 投屏助手")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true) // 建议设置，防止通知被意外划掉
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, createNotification(content))
    }

    override fun onDestroy() {
        // --- 修改：销毁时重置 UI 状态流并取消协程 ---
        resetProgress()
        serviceScope.cancel()
        super.onDestroy()
    }
}