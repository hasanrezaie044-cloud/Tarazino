package ir.tarazino.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import ir.tarazino.ai.AuthStore
import ir.tarazino.ai.BiometricHelper
import ir.tarazino.util.*
import java.util.UUID

@Composable
fun AssistantScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var input by remember { mutableStateOf("") }
    var acc by remember { mutableStateOf(vm.state.accounts.firstOrNull()?.id) }
    val batch = remember(vm.proposals) { UUID.randomUUID().toString() }
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenTitle("دستیار", Icons.Rounded.AutoAwesome)
        ChipRow(ir.tarazino.ai.OfflineAssistant.QUICK, null, { it }) { vm.ask(it) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.chat.isEmpty()) item { EmptyState(Icons.Rounded.AutoAwesome, "بپرس") }
            items(vm.chat) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.Start else Arrangement.End) {
                    Surface(
                        shape = MaterialTheme.shapes.large, tonalElevation = 2.dp,
                        color = if (m.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    ) { Text(m.text, Modifier.padding(12.dp)) }
                }
            }
            if (vm.chatBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (vm.proposals.isNotEmpty()) item {
                AppCard {
                    vm.proposals.forEach { d ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(if (d.isIncome) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown, null, tint = if (d.isIncome) Income else Expense)
                            Text(d.category, Modifier.weight(1f)); Text(Fa.toman(d.amount))
                        }
                    }
                    if (vm.state.accounts.size > 1) ChipRow(vm.state.accounts, vm.state.accounts.firstOrNull { it.id == acc }, { it.name }) { acc = it.id }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconAction(Icons.Rounded.Close, "رد", { vm.clearProposals() })
                        FilledIconButton({ vm.confirmDrafts(batch, vm.proposals, acc); vm.clearProposals() }, enabled = acc != null) { Icon(Icons.Rounded.Check, "ثبت") }
                    }
                }
            }
        }
        OutlinedTextField(
            input, { input = it }, Modifier.fillMaxWidth(), label = { Text("پیام") }, shape = MaterialTheme.shapes.large,
            trailingIcon = { IconAction(Icons.Rounded.Send, "ارسال", { vm.ask(input); input = "" }, !vm.chatBusy && input.isNotBlank(), MaterialTheme.colorScheme.primary) },
        )
    }
}

@Composable
private fun SettingRow(icon: ImageVector, label: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(label, Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, auth: AuthStore, onThemeChange: () -> Unit, onLock: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var theme by remember { mutableStateOf(AppPrefs.theme) }
    var due by remember { mutableStateOf(AppPrefs.notifyDue) }
    var bud by remember { mutableStateOf(AppPrefs.notifyBudget) }
    var saved by remember { mutableStateOf(AppPrefs.notifySaved) }
    var bio by remember { mutableStateOf(auth.biometricEnabled) }
    var confirmWipe by remember { mutableStateOf(false) }
    val bioAvailable = BiometricHelper.available(ctx)
    val version = remember { AppVersion.label(ctx) }
    val themes = listOf("system" to "سیستم", "light" to "روشن", "dark" to "تاریک")

    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle("تنظیمات", Icons.Rounded.Settings)
        AppCard {
            SettingRow(Icons.Rounded.DarkMode, "تم") {}
            ChipRow(themes, themes.first { it.first == theme }, { it.second }) { theme = it.first; AppPrefs.theme = it.first; onThemeChange() }
        }
        AppCard {
            SettingRow(Icons.Rounded.Fingerprint, "ورود با اثر انگشت / چهره") {
                Switch(bio, { want ->
                    if (!want) { auth.biometricEnabled = false; bio = false }
                    else if (bioAvailable) BiometricHelper.prompt(ctx as FragmentActivity, "ترازینو", "انصراف", { auth.biometricEnabled = true; bio = true }, {})
                }, enabled = bioAvailable)
            }
            SettingRow(Icons.Rounded.Lock, "قفل") { IconAction(Icons.Rounded.Lock, "قفل کن", onLock, tint = MaterialTheme.colorScheme.primary) }
        }
        AppCard {
            SettingRow(Icons.Rounded.Event, "سررسیدها") { Switch(due, { due = it; AppPrefs.notifyDue = it }) }
            SettingRow(Icons.Rounded.PieChart, "بودجه") { Switch(bud, { bud = it; AppPrefs.notifyBudget = it }) }
            SettingRow(Icons.Rounded.Notifications, "ثبت تراکنش") { Switch(saved, { saved = it; AppPrefs.notifySaved = it }) }
        }
        AppCard {
            SettingRow(Icons.Rounded.Share, "خروجی CSV") {
                IconAction(Icons.Rounded.Share, "اشتراک‌گذاری", {
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, vm.exportCsv()); putExtra(Intent.EXTRA_SUBJECT, "tarazino.csv") }
                    ctx.startActivity(Intent.createChooser(send, "CSV"))
                }, tint = MaterialTheme.colorScheme.primary)
            }
            SettingRow(Icons.Rounded.Info, "نسخه") { Text(version, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            SettingRow(Icons.Rounded.DeleteForever, "حذف داده‌ها") { IconAction(Icons.Rounded.DeleteForever, "حذف همه", { confirmWipe = true }, tint = Expense) }
        }
    }
    if (confirmWipe) AlertDialog(
        onDismissRequest = { confirmWipe = false }, shape = MaterialTheme.shapes.large, title = { Text("حذف همه؟") },
        confirmButton = { FilledIconButton({ vm.wipeAllLocalData(); confirmWipe = false }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Expense)) { Icon(Icons.Rounded.DeleteForever, "حذف") } },
        dismissButton = { IconButton({ confirmWipe = false }) { Icon(Icons.Rounded.Close, "بستن") } },
    )
}
