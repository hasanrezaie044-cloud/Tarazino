package ir.tarazino.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ir.tarazino.R
import ir.tarazino.domain.Transaction
import ir.tarazino.domain.TxType
import ir.tarazino.util.*
import java.util.UUID

internal fun typeTitle(t: TxType) = when (t) { TxType.INCOME -> "درآمد"; TxType.EXPENSE -> "هزینه"; TxType.TRANSFER -> "انتقال"; TxType.LOAN_PAYMENT -> "قسط" }
internal fun typeIcon(t: TxType): ImageVector = when (t) { TxType.INCOME -> Icons.Rounded.TrendingUp; TxType.EXPENSE -> Icons.Rounded.TrendingDown; TxType.TRANSFER -> Icons.Rounded.SwapHoriz; TxType.LOAN_PAYMENT -> Icons.Rounded.CreditCard }
internal fun typeColor(t: TxType): Color = when (t) { TxType.INCOME -> Income; TxType.TRANSFER -> Accent; else -> Expense }
internal val CATEGORIES = listOf("خوراک", "خرید خانه", "بنزین", "حمل‌ونقل", "قبض", "درمان", "حقوق", "سایر")

@Composable
private fun StatTile(icon: ImageVector, label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Card(modifier, shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = tint)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

@Composable
fun DashboardScreen(vm: AppViewModel, onOpenAccounts: () -> Unit, modifier: Modifier = Modifier) {
    val s = vm.state
    LazyColumn(modifier.padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(painterResource(R.drawable.logo_mark), null, Modifier.size(36.dp))
                Text("ترازینو", style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)), MaterialTheme.shapes.large).padding(22.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.AccountBalanceWallet, null, tint = Color.White)
                        Text("موجودی کل", color = Color.White.copy(alpha = 0.85f))
                    }
                    Text(Fa.toman(s.totalCash), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(s.monthTitle, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Icons.Rounded.TrendingUp, "درآمد", Fa.group(s.monthIncome), Income, Modifier.weight(1f))
                StatTile(Icons.Rounded.TrendingDown, "هزینه", Fa.group(s.monthExpense + s.monthLoanPayments), Expense, Modifier.weight(1f))
            }
        }
        if (s.loaded && s.accounts.isEmpty()) {
            item { EmptyState(Icons.Rounded.AccountBalanceWallet, "حسابی نیست"); }
            item { Button(onOpenAccounts, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("حساب جدید") } }
        }
        items(s.accounts) { a -> ListItemCard(Icons.Rounded.AccountBalanceWallet, a.name, trailing = Fa.group(a.balance)) }
    }
}

@Composable
fun AccountsScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var showAdd by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ScreenTitle("حساب‌ها", Icons.Rounded.AccountBalanceWallet) }
            if (vm.state.accounts.isEmpty()) item { EmptyState(Icons.Rounded.AccountBalanceWallet, "حسابی نیست") }
            items(vm.state.accounts) { a -> ListItemCard(Icons.Rounded.AccountBalanceWallet, a.name, trailing = Fa.group(a.balance)) }
        }
        AddFab({ showAdd = true }, Modifier.align(Alignment.BottomStart).padding(20.dp), "حساب جدید")
    }
    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var opening by remember { mutableStateOf("") }
        FormDialog("حساب جدید", { showAdd = false }, { vm.addAccount(name, opening); showAdd = false }) {
            TextInput(name, { name = it }, "نام", Icons.Rounded.AccountBalanceWallet)
            AmountField(opening, { opening = it }, "موجودی اولیه")
        }
    }
}

@Composable
fun TransactionsScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val s = vm.state
    var filter by remember { mutableStateOf<TxType?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Transaction?>(null) }
    var editing by remember { mutableStateOf<Transaction?>(null) }
    var sentence by remember { mutableStateOf("") }
    var drafts by remember { mutableStateOf<List<TxDraft>>(emptyList()) }
    var draftAccount by remember(s.accounts) { mutableStateOf(s.accounts.firstOrNull()?.id) }
    var batchKey by remember { mutableStateOf(UUID.randomUUID().toString()) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ScreenTitle("تراکنش‌ها", Icons.Rounded.ReceiptLong) }
            item {
                OutlinedTextField(
                    sentence, { sentence = it; drafts = emptyList() }, Modifier.fillMaxWidth(), label = { Text("ثبت سریع با متن") }, shape = MaterialTheme.shapes.medium,
                    leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) },
                    trailingIcon = { IconAction(Icons.Rounded.Send, "استخراج", { drafts = LocalDrafter.draft(sentence, Today.epochDay()); batchKey = UUID.randomUUID().toString() }, sentence.isNotBlank(), MaterialTheme.colorScheme.primary) },
                )
            }
            if (drafts.isNotEmpty()) {
                item {
                    AppCard {
                        drafts.forEach { d ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(if (d.isIncome) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown, null, tint = if (d.isIncome) Income else Expense)
                                Text(d.category, Modifier.weight(1f)); Text(Fa.toman(d.amount))
                            }
                        }
                        if (s.accounts.size > 1) ChipRow(s.accounts, s.accounts.firstOrNull { it.id == draftAccount }, { it.name }) { draftAccount = it.id }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconAction(Icons.Rounded.Close, "رد", { drafts = emptyList() })
                            FilledIconButton({ vm.confirmDrafts(batchKey, drafts, draftAccount); drafts = emptyList(); sentence = "" }, enabled = draftAccount != null) { Icon(Icons.Rounded.Check, "ثبت") }
                        }
                    }
                }
            }
            item { ChipRow(listOf<TxType?>(null) + TxType.entries, filter, { it?.let(::typeTitle) ?: "همه" }) { filter = it } }
            val shown = s.transactions.filter { filter == null || it.type == filter }
            if (shown.isEmpty()) item { EmptyState(Icons.Rounded.ReceiptLong, "تراکنشی نیست") }
            items(shown, key = { it.id }) { t ->
                val acc = s.accounts.firstOrNull { it.id == t.accountId }?.name ?: ""
                val sign = when (t.type) { TxType.INCOME -> "+"; TxType.TRANSFER -> ""; else -> "−" }
                ListItemCard(typeIcon(t.type), t.categoryId ?: typeTitle(t.type), JalaliDate.fromEpochDay(t.epochDay).format() + " · " + acc, sign + Fa.group(t.amount), typeColor(t.type)) {
                    if (t.type != TxType.LOAN_PAYMENT) IconAction(Icons.Rounded.Edit, "ویرایش", { editing = t })
                    IconAction(Icons.Rounded.Delete, "حذف", { toDelete = t })
                }
            }
        }
        AddFab({ showAdd = true }, Modifier.align(Alignment.BottomStart).padding(20.dp), "تراکنش جدید")
    }

    editing?.let { t -> EditTransactionDialog(vm, t) { editing = null } }
    toDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { toDelete = null }, shape = MaterialTheme.shapes.large, title = { Text("حذف؟") }, text = { Text(Fa.toman(t.amount)) },
            confirmButton = { FilledIconButton({ vm.delete(t.id); toDelete = null }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Expense)) { Icon(Icons.Rounded.Delete, "حذف") } },
            dismissButton = { IconButton({ toDelete = null }) { Icon(Icons.Rounded.Close, "بستن") } },
        )
    }
    if (showAdd && s.accounts.isNotEmpty()) {
        val key = remember { UUID.randomUUID().toString() }
        var type by remember { mutableStateOf(TxType.EXPENSE) }
        var amount by remember { mutableStateOf("") }
        var acc by remember { mutableStateOf(s.accounts.first().id) }
        var to by remember { mutableStateOf(s.accounts.getOrNull(1)?.id) }
        var date by remember { mutableStateOf(Today.jalali().format().let { Fa.normalizeDigits(it) }) }
        var cat by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        FormDialog("تراکنش جدید", { showAdd = false }, { vm.addTransaction(key, type, amount, acc, if (type == TxType.TRANSFER) to else null, date, cat, note); showAdd = false }) {
            ChipRow(listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER), type, ::typeTitle, ::typeIcon) { type = it }
            AmountField(amount, { amount = it }, "مبلغ")
            ChipRow(s.accounts, s.accounts.firstOrNull { it.id == acc }, { it.name }) { acc = it.id }
            if (type == TxType.TRANSFER) ChipRow(s.accounts, s.accounts.firstOrNull { it.id == to }, { "به " + it.name }) { to = it.id }
            else { ChipRow(CATEGORIES, cat, { it }) { cat = it }; TextInput(cat, { cat = it }, "دسته", Icons.Rounded.PieChart) }
            DateField(date, { date = it }, "تاریخ")
            TextInput(note, { note = it }, "توضیح", Icons.Rounded.Edit)
        }
    }
}

@Composable
fun EditTransactionDialog(vm: AppViewModel, t: Transaction, onDismiss: () -> Unit) {
    var amt by remember { mutableStateOf(t.amount.toString()) }
    var date by remember { mutableStateOf(Fa.normalizeDigits(JalaliDate.fromEpochDay(t.epochDay).format())) }
    var cat by remember { mutableStateOf(t.categoryId ?: "") }
    var note by remember { mutableStateOf(t.note) }
    FormDialog("ویرایش", onDismiss, { vm.editTransaction(t, amt, date, cat, note); onDismiss() }) {
        AmountField(amt, { amt = it }, "مبلغ")
        DateField(date, { date = it }, "تاریخ")
        TextInput(cat, { cat = it }, "دسته", Icons.Rounded.PieChart)
        TextInput(note, { note = it }, "توضیح", Icons.Rounded.Edit)
    }
}
