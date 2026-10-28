package ir.tarazino

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.tarazino.ai.AiSettingsStore
import ir.tarazino.ai.AuthStore
import ir.tarazino.ui.*
import ir.tarazino.util.AppPrefs
import ir.tarazino.util.DueWorker

class MainActivity : FragmentActivity() {
    private val unlocked = mutableStateOf(false)
    private var stoppedAt = 0L

    override fun onStop() { super.onStop(); stoppedAt = SystemClock.elapsedRealtime() }
    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > 60_000) unlocked.value = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPrefs.init(applicationContext)
        DueWorker.schedule(applicationContext)
        val store = AiSettingsStore(applicationContext)
        val auth = AuthStore(applicationContext)
        setContent {
            var themeKey by remember { mutableStateOf(AppPrefs.theme) }
            val dark = when (themeKey) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            LaunchedEffect(unlocked.value) { if (unlocked.value && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                val vm: AppViewModel = viewModel()
                if (!unlocked.value) {
                    AuthScreen(auth, this@MainActivity, { unlocked.value = true }, { auth.clear(); vm.wipeAllLocalData() })
                } else TarazinoTheme(dark) {
                    AppShell(vm, store, auth, { themeKey = AppPrefs.theme }, { unlocked.value = false })
                }
            }
        }
    }
}

private data class Tab(val title: String, val icon: ImageVector)

@Composable
private fun AppShell(vm: AppViewModel, store: AiSettingsStore, auth: AuthStore, onTheme: () -> Unit, onLock: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var more by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    BackHandler(enabled = more != null) { more = null }
    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.message = null } }
    val tabs = listOf(Tab("خانه", Icons.Rounded.Dashboard), Tab("تراکنش‌ها", Icons.Rounded.ReceiptLong), Tab("گزارش", Icons.Rounded.Insights), Tab("دستیار", Icons.Rounded.AutoAwesome), Tab("بیشتر", Icons.Rounded.GridView))
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
                tabs.forEachIndexed { i, t -> NavigationBarItem(selected = tab == i, onClick = { tab = i; more = null }, icon = { Icon(t.icon, t.title) }, label = { Text(t.title, maxLines = 1) }) }
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        when (tab) {
            0 -> DashboardScreen(vm, { tab = 4; more = "accounts" }, m)
            1 -> TransactionsScreen(vm, m)
            2 -> ReportsScreen(vm, m)
            3 -> AssistantScreen(vm, m)
            else -> Box(m) {
                when (more) {
                    "accounts" -> AccountsScreen(vm)
                    "budgets" -> BudgetsScreen(vm)
                    "goals" -> GoalsScreen(vm)
                    "loans" -> LoansManageScreen(vm)
                    "calc" -> LoanScreen()
                    "commitments" -> CommitmentsScreen(vm)
                    "ai" -> AiSettingsScreen(store)
                    "settings" -> SettingsScreen(vm, auth, onTheme, onLock)
                    else -> MoreMenu { more = it }
                }
                if (more != null) IconButton({ more = null }, Modifier.align(Alignment.TopEnd).padding(8.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "بازگشت") }
            }
        }
    }
}

@Composable
private fun MoreMenu(onPick: (String) -> Unit) {
    val entries = listOf(
        Triple("accounts", "حساب‌ها", Icons.Rounded.AccountBalanceWallet), Triple("budgets", "بودجه", Icons.Rounded.PieChart), Triple("goals", "اهداف", Icons.Rounded.Flag),
        Triple("loans", "وام‌ها", Icons.Rounded.CreditCard), Triple("calc", "ماشین‌حساب وام", Icons.Rounded.Calculate), Triple("commitments", "چک و تعهدات", Icons.Rounded.Event),
        Triple("ai", "هوش مصنوعی", Icons.Rounded.SmartToy), Triple("settings", "تنظیمات", Icons.Rounded.Settings),
    )
    LazyColumn(Modifier.padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenTitle("بیشتر", Icons.Rounded.GridView) }
        items(entries) { (k, title, icon) ->
            Card(onClick = { onPick(k) }, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
