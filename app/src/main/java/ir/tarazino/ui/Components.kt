package ir.tarazino.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import ir.tarazino.util.Fa

/** نمایش زندهٔ مبلغ با جداکنندهٔ هزارگان و ارقام فارسی؛ مقدار ذخیره‌شده فقط رقم لاتین است. */
object AmountTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val n = raw.length
        val out = StringBuilder()
        val toTrans = IntArray(n + 1)
        val toOrig = ArrayList<Int>()
        for (i in 0 until n) {
            if (i > 0 && (n - i) % 3 == 0) { out.append('٬'); toOrig.add(i) }
            toTrans[i] = out.length
            out.append('۰' + (raw[i] - '0'))
            toOrig.add(i)
        }
        toTrans[n] = out.length
        toOrig.add(n)
        return TransformedText(AnnotatedString(out.toString()), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = toTrans[offset.coerceIn(0, n)]
            override fun transformedToOriginal(offset: Int) = toOrig[offset.coerceIn(0, toOrig.size - 1)]
        })
    }
}

private fun digitsOnly(s: String, max: Int) = Fa.normalizeDigits(s).filter { it in '0'..'9' }.take(max)

@Composable
fun AmountField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Rounded.Payments) {
    OutlinedTextField(
        value, { onChange(digitsOnly(it, 15)) }, modifier.fillMaxWidth(), singleLine = true, label = { Text(label) },
        leadingIcon = { Icon(icon, null) }, suffix = { Text("تومان") }, shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), visualTransformation = AmountTransformation,
    )
}

@Composable
fun IntField(value: String, onChange: (String) -> Unit, label: String, icon: ImageVector, modifier: Modifier = Modifier, maxLen: Int = 4) {
    OutlinedTextField(
        value, { onChange(digitsOnly(it, maxLen)) }, modifier.fillMaxWidth(), singleLine = true, label = { Text(label) },
        leadingIcon = { Icon(icon, null) }, shape = MaterialTheme.shapes.medium, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
fun DecimalField(value: String, onChange: (String) -> Unit, label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value, { raw ->
            val t = Fa.normalizeDigits(raw).replace(',', '.').filter { it in '0'..'9' || it == '.' }.take(6)
            onChange(if (t.count { it == '.' } > 1) value else t)
        },
        modifier.fillMaxWidth(), singleLine = true, label = { Text(label) }, leadingIcon = { Icon(icon, null) }, suffix = { Text("٪") },
        shape = MaterialTheme.shapes.medium, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

/** تاریخ شمسی با کیبورد عددی؛ جداکنندهٔ «/» خودکار درج می‌شود. */
@Composable
fun DateField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        Fa.digits(value), { raw ->
            val d = digitsOnly(raw, 8)
            onChange(buildString { d.forEachIndexed { i, c -> if (i == 4 || i == 6) append('/'); append(c) } })
        },
        modifier.fillMaxWidth(), singleLine = true, label = { Text(label) }, leadingIcon = { Icon(Icons.Rounded.Event, null) },
        shape = MaterialTheme.shapes.medium, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
fun TextInput(value: String, onChange: (String) -> Unit, label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), singleLine = true, label = { Text(label) }, leadingIcon = { Icon(icon, null) }, shape = MaterialTheme.shapes.medium)
}

@Composable
fun <T> ChipRow(items: List<T>, selected: T?, label: (T) -> String, icon: ((T) -> ImageVector)? = null, onSelect: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items) { i ->
            FilterChip(
                selected = i == selected, onClick = { onSelect(i) }, label = { Text(label(i)) }, shape = CircleShape,
                leadingIcon = icon?.let { f -> { Icon(f(i), null, Modifier.size(18.dp)) } },
            )
        }
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp),
    ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}

@Composable
fun ListItemCard(icon: ImageVector, title: String, subtitle: String? = null, trailing: String? = null, tint: Color = MaterialTheme.colorScheme.primary, actions: @Composable RowScope.() -> Unit = {}) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = tint.copy(alpha = 0.14f), modifier = Modifier.size(42.dp)) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint) } }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (trailing != null) Text(trailing, style = MaterialTheme.typography.titleMedium, color = tint)
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth(), content = actions)
    }
}

@Composable
fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit, enabled: Boolean = true, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    IconButton(onClick, enabled = enabled) { Icon(icon, description, tint = tint) }
}

@Composable
fun EmptyState(icon: ImageVector, text: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ScreenTitle(title: String, icon: ImageVector, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun AddFab(onClick: () -> Unit, modifier: Modifier = Modifier, description: String = "افزودن") {
    FloatingActionButton(onClick, modifier, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = CircleShape) {
        Icon(Icons.Rounded.Add, description)
    }
}

@Composable
fun FormDialog(title: String, onDismiss: () -> Unit, onOk: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, shape = MaterialTheme.shapes.large, title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } },
        confirmButton = { FilledIconButton(onOk) { Icon(Icons.Rounded.Check, "تأیید") } },
        dismissButton = { IconButton(onDismiss) { Icon(Icons.Rounded.Close, "بستن") } },
    )
}
