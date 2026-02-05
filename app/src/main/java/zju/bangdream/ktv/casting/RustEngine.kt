package zju.bangdream.ktv.casting

object RustEngine {
    init {
        System.loadLibrary("ktv_casting")
    }

    // 基础接口
    external fun initLogging(level: Int)
    external fun searchDevices(): Array<DlnaDeviceItem>

    // 核心初始化：启动 Rust 内部的 HttpServer 和状态机
    external fun startEngine(baseUrl: String, roomId: Long, targetLocation: String)

    // 控制接口：由 KT 决定何时调用
    external fun nextSong()

    // 查询接口：由 KT 轮询获取状态
    external fun queryProgress(): Long      // 返回当前秒数
    external fun queryTotalDuration(): Long // 返回总秒数
}