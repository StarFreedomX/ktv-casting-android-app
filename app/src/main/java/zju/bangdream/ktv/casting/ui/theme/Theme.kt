package zju.bangdream.ktv.casting.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = PopipaPink,
    onPrimary = Color.White,
    primaryContainer = LightPinkContainer,
    onPrimaryContainer = PopipaPink,
    secondary = PopipaPink,
    tertiary = DarkGray,
    background = Color.White
    // 你可以根据需要继续添加 surface, error 等颜色
)

@Composable
fun KtvCastingTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        // 如果你有自定义的 Typography 或 Shapes，也可以在这里添加
        content = content
    )
}