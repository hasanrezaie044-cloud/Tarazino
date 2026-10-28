package ir.tarazino.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.tarazino.data.ObligationEntity
import ir.tarazino.domain.*
import ir.tarazino.util.*
import java.util.UUID

@Composable
private fun ListScreen(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onAdd: (() -> Unit)?, modifier: Modifier, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ScreenTitle(title, icon) }
            content()
        }
        if (onAdd != null) AddFab(onAdd, Modifier.align(Alignment.BottomStart).padding(20.dp))
    }
}

@Composable
fun BudgetsScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var add by remember { mutableStateOf(false) }
    ListScreen("بودجه", Icons.Rounded.PieChart, { add = true }, modifier) {
        if (vm.state.budgets.isEmpty()) item { EmptyState(Icons.Rounded.PieChart, "بودجه‌ای نیست") }
        items(vm.state.budgets) { r ->
            val pct = (r.spent * 100 / r.budget.monthlyLimit).toInt()
            val over = pct >= r.budget.warnPercent
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.budget.category, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(Fa.digits("$pct") + "٪", color = if (over) Expense else MaterialTheme.colorScheme.primary)
                    IconAction(Icons.Rounded.Delete, "حذف", { vm.deleteBudget(r.budget.id) })
                }
                LinearProgressIndicator(progress = { (pct / 100f).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(8.dp), color = if (over) Expense else MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(Fa.toman(r.spent), style = MaterialTheme.typography.bodySmall)
                    Text(Fa.toman(r.budget.monthlyLimit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (add) {
        var cat by remember { mutableStateOf("") }; var limit by remember { mutableStateOf("") }; var warn by remember { mutableStateOf("80") }
        FormDialog("بودجه", { add = false }, { vm.saveBudget(cat, limit, warn); add = false }) {
            ChipRow(CATEGORIES, cat, { it }) { cat = it }
            TextInput(cat, { cat = it }, "دسته", Icons.Rounded.PieChart)
            AmountField(limit, { limit = it }, "سقف ماهانه")
            IntField(warn, { warn = it }, "هشدار (٪)", Icons.Rounded.Notifications, maxLen = 3)
        }
    }
}

@Composable
fun GoalsScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var add by remember { mutableStateOf(false) }
    var depositFor by remember { mutableStateOf<String?>(null) }
    ListScreen("اهداف", Icons.Rounded.Flag, { add = true }, modifier) {
        val goals = vm.state.snap?.goals.orEmpty()
        if (goals.isEmpty()) item { EmptyState(Icons.Rounded.Flag, "هدفی نیست") }
        items(goals) { g ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Savings, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(g.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconAction(Icons.Rounded.Add, "واریز", { depositFor = g.id }, tint = MaterialTheme.colorScheme.primary)
                    IconAction(Icons.Rounded.Delete, "حذف", { vm.deleteGoal(g.id) })
                }
                LinearProgressIndicator(progress = { (g.deposited.toFloat() / g.target).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(8.dp), trackColor = MaterialTheme.colorScheme.surfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(Fa.toman(g.deposited), style = MaterialTheme.typography.bodySmall)
                    Text(Fa.toman(g.target), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                g.deadlineEpochDay?.let { Text(JalaliDate.fromEpochDay(it).format(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    if (add) {
        var name by remember { mutableStateOf("") }; var target by remember { mutableStateOf("") }; var dl by remember { mutableStateOf("") }
        FormDialog("هدف", { add = false }, { vm.saveGoal(name, target, dl); add = false }) {
            TextInput(name, { name = it }, "نام", Icons.Rounded.Flag); AmountField(target, { target = it }, "مبلغ هدف"); DateField(dl, { dl = it }, "مهلت")
        }
    }
    depositFor?.let { id ->
        val key = remember { UUID.randomUUID().toString() }
        var amt by remember { mutableStateOf("") }
        FormDialog("واریز", { depositFor = null }, { vm.depositGoal(key, id, amt); depositFor = null }) { AmountField(amt, { amt = it }, "مبلغ", icon = Icons.Rounded.Savings) }
    }
}

@Composable
fun LoansManageScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var add by remember { mutableStateOf(false) }
    var payFor by remember { mutableStateOf<Pair<LoanInfo, Int>?>(null) }
    val snap = vm.state.snap
    ListScreen("وام‌ها", Icons.Rounded.CreditCard, { add = true }, modifier) {
        val loans = snap?.loans.orEmpty()
        if (loans.isEmpty()) item { EmptyState(Icons.Rounded.CreditCard, "وامی نیست") }
        items(loans) { l ->
            val paid = snap!!.paidInstallments[l.id].orEmpty(); val next = LoanPlan.nextUnpaid(l, paid)
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(l.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconAction(Icons.Rounded.Delete, "حذف", { vm.deleteLoan(l.id) })
                }
                LinearProgressIndicator(progress = { paid.size.toFloat() / l.months }, Modifier.fillMaxWidth().height(8.dp), trackColor = MaterialTheme.colorScheme.surfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(Fa.digits("${paid.size}") + "/" + Fa.digits("${l.months}"), style = MaterialTheme.typography.bodySmall)
                    Text(Fa.toman(LoanPlan.remainingDebt(l, paid)), style = MaterialTheme.typography.bodySmall)
                }
                if (next != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Fa.toman(next.total), style = MaterialTheme.typography.titleMedium)
                        Text(JalaliDate.fromEpochDay(l.dueDay(next.no)).format(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalButton({ payFor = l to next.no }, enabled = vm.state.accounts.isNotEmpty()) { Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(6.dp)); Text("پرداخت") }
                } else Icon(Icons.Rounded.Check, "پرداخت کامل", tint = Income)
            }
        }
    }
    if (add) {
        var preset by remember { mutableStateOf(LoanCatalog.presets.first()) }
        var title by remember { mutableStateOf("") }; var amt by remember { mutableStateOf("") }; var months by remember { mutableStateOf(preset.maxMonths.toString()) }
        var rate by remember { mutableStateOf(preset.defaultRatePercent?.toString() ?: "") }; var given by remember { mutableStateOf("") }
        var first by remember { mutableStateOf(Fa.normalizeDigits(Today.jalali().plusMonths(1).format())) }
        var method by remember { mutableStateOf(preset.method) }
        FormDialog("وام", { add = false }, { vm.saveLoan(title.ifBlank { preset.titleFa }, preset.id, amt, months, rate, method, given, first); add = false }) {
            ChipRow(LoanCatalog.presets, preset, { it.titleFa }) { preset = it; method = it.method; rate = it.defaultRatePercent?.toString() ?: ""; months = it.maxMonths.toString() }
            TextInput(title, { title = it }, "عنوان", Icons.Rounded.Edit)
            AmountField(amt, { amt = it }, "مبلغ وام")
            IntField(months, { months = it }, "تعداد اقساط", Icons.Rounded.Event, maxLen = 3)
            DecimalField(rate, { rate = it }, "نرخ سالانه", Icons.Rounded.TrendingUp)
            ChipRow(LoanMethod.entries, method, { it.shortFa }) { method = it }
            if (method == LoanMethod.GIVEN_INSTALLMENT) AmountField(given, { given = it }, "قسط ماهانه", icon = Icons.Rounded.CreditCard)
            DateField(first, { first = it }, "سررسید قسط اول")
        }
    }
    payFor?.let { (loan, no) ->
        var acc by remember { mutableStateOf(vm.state.accounts.firstOrNull()?.id) }
        FormDialog(loan.title + " · " + Fa.digits("$no"), { payFor = null }, { vm.payInstallment(loan, no, acc); payFor = null }) {
            ChipRow(vm.state.accounts, vm.state.accounts.firstOrNull { it.id == acc }, { it.name }) { acc = it.id }
        }
    }
}

@Composable
fun CommitmentsScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var addCheque by remember { mutableStateOf(false) }
    var addObl by remember { mutableStateOf(false) }
    var settle by remember { mutableStateOf<ObligationEntity?>(null) }
    val snap = vm.state.snap
    ListScreen("چک و تعهدات", Icons.Rounded.Event, null, modifier) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton({ addCheque = true }, enabled = vm.state.accounts.isNotEmpty()) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("چک") }
                FilledTonalButton({ addObl = true }) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("طلب/بدهی") }
            }
        }
        val cheques = snap?.cheques.orEmpty(); val obls = snap?.obligations.orEmpty()
        if (cheques.isEmpty() && obls.isEmpty()) item { EmptyState(Icons.Rounded.Event, "موردی نیست") }
        items(cheques) { c ->
            val paid = c.status == "PAID"
            ListItemCard(Icons.Rounded.Payments, if (c.note.isBlank()) "چک" else c.note, JalaliDate.fromEpochDay(c.dueEpochDay).format(), Fa.group(c.amount), if (paid) Income else Expense) {
                if (!paid) IconAction(Icons.Rounded.Check, "پرداخت شد", { vm.payCheque(c) }, tint = Income)
                IconAction(Icons.Rounded.Delete, "حذف", { vm.deleteCheque(c.id) })
            }
        }
        items(obls) { o ->
            val paid = o.status == "PAID"
            ListItemCard(if (o.isReceivable) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown, o.counterparty, o.dueEpochDay?.let { JalaliDate.fromEpochDay(it).format() }, Fa.group(o.amount), if (paid) MaterialTheme.colorScheme.outline else if (o.isReceivable) Income else Expense) {
                if (!paid) IconAction(Icons.Rounded.Check, if (o.isReceivable) "دریافت شد" else "پرداخت شد", { settle = o }, vm.state.accounts.isNotEmpty(), Income)
                IconAction(Icons.Rounded.Delete, "حذف", { vm.deleteObligation(o.id) })
            }
        }
    }
    if (addCheque) {
        var amt by remember { mutableStateOf("") }; var due by remember { mutableStateOf(Fa.normalizeDigits(Today.jalali().format())) }; var note by remember { mutableStateOf("") }
        var acc by remember { mutableStateOf(vm.state.accounts.firstOrNull()?.id) }
        FormDialog("چک", { addCheque = false }, { vm.saveCheque(amt, due, acc, note); addCheque = false }) {
            AmountField(amt, { amt = it }, "مبلغ"); DateField(due, { due = it }, "سررسید"); TextInput(note, { note = it }, "توضیح", Icons.Rounded.Edit)
            ChipRow(vm.state.accounts, vm.state.accounts.firstOrNull { it.id == acc }, { it.name }) { acc = it.id }
        }
    }
    if (addObl) {
        var name by remember { mutableStateOf("") }; var amt by remember { mutableStateOf("") }; var due by remember { mutableStateOf("") }; var rec by remember { mutableStateOf(true) }; var note by remember { mutableStateOf("") }
        FormDialog("طلب/بدهی", { addObl = false }, { vm.saveObligation(name, amt, due, rec, note); addObl = false }) {
            ChipRow(listOf(true, false), rec, { if (it) "طلب" else "بدهی" }, { if (it) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown }) { rec = it }
            TextInput(name, { name = it }, "طرف حساب", Icons.Rounded.Person); AmountField(amt, { amt = it }, "مبلغ"); DateField(due, { due = it }, "موعد"); TextInput(note, { note = it }, "توضیح", Icons.Rounded.Edit)
        }
    }
    settle?.let { o ->
        var acc by remember { mutableStateOf(vm.state.accounts.firstOrNull()?.id) }
        FormDialog(Fa.toman(o.amount), { settle = null }, { vm.settle(o, acc); settle = null }) {
            ChipRow(vm.state.accounts, vm.state.accounts.firstOrNull { it.id == acc }, { it.name }) { acc = it.id }
        }
    }
}
