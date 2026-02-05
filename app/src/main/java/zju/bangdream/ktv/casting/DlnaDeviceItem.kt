package zju.bangdream.ktv.casting

/**
 * 用于 Rust JNI 传递设备信息到 Kotlin 的数据模型
 */
data class DlnaDeviceItem(
    val name: String,
    val location: String
)