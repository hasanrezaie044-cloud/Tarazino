package ir.tarazino.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ir.tarazino.ai.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AiSettingsScreen(store: AiSettingsStore, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf(store.activeProvider) }
    var enabled by remember { mutableStateOf(store.aiEnabled) }
    var url by remember(provider) { mutableStateOf(store.config(provider).baseUrl) }
    var model by remember(provider) { mutableStateOf(store.config(provider).model) }
    var keyInput by remember(provider) { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var masked by remember(provider) { mutableStateOf(store.maskedKey(provider)) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle("هوش مصنوعی", Icons.Rounded.AutoAwesome)
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("دستیار", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(enabled, { enabled = it; store.aiEnabled = it })
            }
        }
        ChipRow(ProviderId.entries, provider, { it.titleFa }) { provider = it; store.activeProvider = it; status = null }
        TextInput(url, { url = it }, "آدرس API", Icons.Rounded.Language)
        TextInput(model, { model = it }, "مدل", Icons.Rounded.SmartToy)
        OutlinedTextField(
            keyInput, { keyInput = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, shape = MaterialTheme.shapes.medium,
            label = { Text(if (masked != null) masked!! else "کلید API") }, leadingIcon = { Icon(Icons.Rounded.Key, null) },
            trailingIcon = { IconAction(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "پنهان" else "نمایش", { show = !show }) },
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button({
                if (!url.trim().startsWith("https://")) { status = "آدرس باید HTTPS باشد"; return@Button }
                store.saveConfig(provider, ProviderConfig(url, model))
                if (keyInput.isNotBlank()) { store.saveKey(provider, keyInput); keyInput = ""; masked = store.maskedKey(provider) }
                status = "ذخیره شد"
            }) { Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(6.dp)); Text("ذخیره") }
            FilledTonalButton(enabled = !busy && masked != null, onClick = {
                busy = true; status = "…"
                scope.launch {
                    val r = withContext(Dispatchers.IO) { AiClient(store).chat(provider, listOf(ChatMessage("user", "سلام")), 20) }
                    status = when (r) { is AiResult.Ok -> "اتصال موفق"; is AiResult.Error -> r.messageFa }
                    busy = false
                }
            }) { Icon(Icons.Rounded.Bolt, null); Spacer(Modifier.width(6.dp)); Text("آزمون") }
            IconAction(Icons.Rounded.Delete, "حذف کلید", { store.deleteKey(provider); masked = null; status = "کلید حذف شد" }, masked != null, Expense)
        }
        status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
