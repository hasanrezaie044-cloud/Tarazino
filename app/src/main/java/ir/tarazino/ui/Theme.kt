package ir.tarazino.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Income = Color(0xFF16A870)
val Expense = Color(0xFFE5484D)
val Accent = Color(0xFF3B82F6)

private val Light = lightColorScheme(
    primary = Color(0xFF0E9F6E), onPrimary = Color.White, primaryContainer = Color(0xFFD7F5E8), onPrimaryContainer = Color(0xFF053D2A),
    secondary = Color(0xFF1D6FD1), background = Color(0xFFF4F6FA), surface = Color.White, onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEBEFF5), onSurfaceVariant = Color(0xFF5B6575), outline = Color(0xFFD3DAE4), error = Color(0xFFD93025),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF2EE6A6), onPrimary = Color(0xFF00382A), primaryContainer = Color(0xFF0F3B33), onPrimaryContainer = Color(0xFFBFF5E0),
    secondary = Color(0xFF4DB8FF), background = Color(0xFF071326), surface = Color(0xFF0E1D36), onSurface = Color(0xFFE8EEF8),
    surfaceVariant = Color(0xFF14284A), onSurfaceVariant = Color(0xFF9AA8BF), outline = Color(0xFF28406A), error = Color(0xFFFF6B6B),
)
private val Shapes3 = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp))
private val Type3 = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp), bodyMedium = TextStyle(fontSize = 14.sp),
    labelMedium = TextStyle(fontSize = 12.sp), labelSmall = TextStyle(fontSize = 11.sp),
)

@Composable
fun TarazinoTheme(dark: Boolean, content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (dark) Dark else Light, shapes = Shapes3, typography = Type3, content = content)
