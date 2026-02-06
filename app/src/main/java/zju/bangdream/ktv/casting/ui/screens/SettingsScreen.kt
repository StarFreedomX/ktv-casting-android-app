package zju.bangdream.ktv.casting.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 状态：各种权限检测
    var isIgnoringBattery by remember { mutableStateOf(checkBatteryOptimizations(context)) }
    var isNotificationEnabled by remember { mutableStateOf(checkNotificationPermission(context)) }

    // 自动刷新逻辑
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringBattery = checkBatteryOptimizations(context)
                isNotificationEnabled = checkNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("后台运行设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isIgnoringBattery = checkBatteryOptimizations(context)
                        isNotificationEnabled = checkNotificationPermission(context)
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "手动刷新")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "请确保以下状态为“已允许”，以防投屏中途断开。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // --- 1. 通知权限 (新增) ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "1. 通知权限", style = MaterialTheme.typography.titleMedium)
                        Badge(containerColor = if (isNotificationEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) {
                            Text(if (isNotificationEnabled) "已允许" else "未允许", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Text(
                        text = "前台通知是维持后台运行的关键，请务必开启。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Button(
                        onClick = { openNotificationSettings(context) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("去设置")
                    }
                }
            }

            // --- 2. 电池优化 ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "2. 忽略电池优化", style = MaterialTheme.typography.titleMedium)
                        Badge(containerColor = if (isIgnoringBattery) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) {
                            Text(if (isIgnoringBattery) "已允许" else "未允许", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Text(
                        text = "允许应用不受到系统用电限制。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Button(
                        onClick = { requestIgnoreBatteryOptimizations(context) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("去设置")
                    }
                }
            }

            // --- 3. 厂商后台管理 ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "3. 厂商自启动管理", style = MaterialTheme.typography.titleMedium)
                    val brand = Build.BRAND.uppercase()
                    Text(
                        text = "当前检测到机型: $brand。请开启“自启动”并关闭“省电策略限制”。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        OutlinedButton(onClick = { openAppDetails(context) }) {
                            Text("应用详情")
                        }
                        Button(onClick = { goManufacturerSetting(context) }) {
                            Text("跳转管理页")
                        }
                    }
                }
            }
        }
    }
}

// --- 工具方法 ---

private fun checkNotificationPermission(context: Context): Boolean {
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

private fun openNotificationSettings(context: Context) {
    try {
        val intent = Intent().apply {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                else -> {
                    action = "android.settings.APP_NOTIFICATION_SETTINGS"
                    putExtra("app_package", context.packageName)
                    putExtra("app_uid", context.applicationInfo.uid)
                }
            }
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        openAppDetails(context)
    }
}

private fun checkBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    return pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:${context.packageName}".toUri()
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}

private fun openAppDetails(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// --- 厂商跳转逻辑适配 ---

private fun showActivity(context: Context, packageName: String, activityDir: String? = null) {
    val intent = if (activityDir == null) {
        context.packageManager.getLaunchIntentForPackage(packageName)
    } else {
        Intent().apply {
            component = ComponentName(packageName, activityDir)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    context.startActivity(intent)
}

private fun goManufacturerSetting(context: Context) {
    val brand = Build.BRAND?.lowercase() ?: ""
    try {
        when {
            brand.contains("huawei") || brand.contains("honor") -> {
                try {
                    showActivity(context, "com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
                } catch (e: Exception) {
                    showActivity(context, "com.huawei.systemmanager", "com.huawei.systemmanager.optimize.bootstart.BootStartActivity")
                }
            }
            brand.contains("xiaomi") || brand.contains("redmi") -> {
                showActivity(context, "com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            }
            brand.contains("oppo") -> {
                try { showActivity(context, "com.coloros.phonemanager") }
                catch (e: Exception) {
                    try { showActivity(context, "com.oppo.safe") }
                    catch (e: Exception) {
                        try { showActivity(context, "com.coloros.oppoguardelf") }
                        catch (e: Exception) { showActivity(context, "com.coloros.safecenter") }
                    }
                }
            }
            brand.contains("vivo") -> {
                showActivity(context, "com.iqoo.secure")
            }
            brand.contains("meizu") -> {
                showActivity(context, "com.meizu.safe")
            }
            brand.contains("samsung") -> {
                try { showActivity(context, "com.samsung.android.sm_cn") }
                catch (e: Exception) { showActivity(context, "com.samsung.android.sm") }
            }
            brand.contains("letv") -> {
                showActivity(context, "com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")
            }
            brand.contains("smartisan") -> {
                showActivity(context, "com.smartisanos.security")
            }
            else -> openAppDetails(context)
        }
    } catch (e: Exception) {
        openAppDetails(context)
    }
}