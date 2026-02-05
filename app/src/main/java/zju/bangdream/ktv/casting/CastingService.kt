package zju.bangdream.ktv.casting

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class CastingService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val baseUrl = intent?.getStringExtra("base_url") ?: ""
        val roomId = intent?.getLongExtra("room_id", 0L) ?: 0L
        val location = intent?.getStringExtra("location") ?: ""

        // 1. 立即显示通知栏（Android 要求）
        startForeground(1, createNotification("准备投屏..."))
        val notification = createNotification("准备投屏...")

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

        // 3. 开启“指挥官”轮询逻辑
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
                Log.d("KTV_SERVICE", "当前进度: $current / 总计: $total")
                if (current >= 0 && total > 0) {
                    // 更新通知栏显示进度
                    updateNotification("正在播放: $current / $total 秒")

                    // --- 核心控制逻辑 ---
                    // 如果距离结束不到 2 秒，下达切歌指令
                    if (total - current <= 2 && current > 5) { // current > 5 防止新歌刚开始就误切
                        Log.i("KTV_CMD", "距离结束仅剩 ${total - current}s，KT 下达切歌指令")
                        RustEngine.nextSong()

                        // 切歌后强制等待 5 秒，防止重复触发
                        delay(5000)
                    }
                }

                delay(1000) // 每秒轮询一次
            }
        }
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
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, notification)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}