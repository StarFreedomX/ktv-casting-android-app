package zju.bangdream.ktv.casting

object RustEngine {
    init {
        System.loadLibrary("ktv_casting_lib")
    }

    // 基础接口
    external fun initLogging(level: Int)
    external fun searchDevices(): Array<DlnaDeviceItem>

    // 核心初始化：启动 Rust 内部的 HttpServer 和状态机
    external fun startEngine(baseUrl: String, roomId: String, targetLocation: String)

    // 控制接口：由 KT 决定何时调用
    external fun nextSong()

    // 查询接口：由 KT 轮询获取状态
    external fun queryProgress(): Long      // 返回当前秒数

    external fun queryTotalDuration(): Long // 返回总秒数

    external fun resetEngine()
    /**
     * @return 1 为播放中，0 为暂停
     */
    external fun togglePause(): Int

    /**
     * @param target 目标值
     * @return 新音量，-1 为失败
     */
    external fun setVolume(target: Int): Int
    /**
     * @return 音量，-1 为失败
     */
    external fun getVolume(): Int

    /**
     * @return 音量，-1 为失败
     */
    external fun jumpToSecs(target: Int): Int
}