package ir.tarazino.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.tarazino.domain.*
import ir.tarazino.util.Fa

@Composable
fun LoanScreen(modifier: Modifier = Modifier) {
    var presetId by remember { mutableStateOf("marriage") }
    val preset = LoanCatalog.byId(presetId)!!
    var amount by remember(presetId) { mutableStateOf(preset.maxAmountToman?.toString() ?: "") }
    var months by remember(presetId) { mutableStateOf(preset.maxMonths.toString()) }
    var rate by remember(presetId) { mutableStateOf(preset.defaultRatePercent?.toString() ?: "") }
    var given by remember(presetId) { mutableStateOf("") }
    var method by remember(presetId) { mutableStateOf(preset.method) }
    var schedule by remember { mutableStateOf<LoanSchedule?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(modifier.padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenTitle("ماشین‌حساب وام", Icons.Rounded.Calculate) }
        item { ChipRow(LoanCatalog.presets, preset, { it.titleFa }) { presetId = it.id; schedule = null; error = null } }
        item { AmountField(amount, { amount = it }, "مبلغ وام") }
        item { IntField(months, { months = it }, "تعداد اقساط", Icons.Rounded.Event, maxLen = 3) }
        item { DecimalField(rate, { rate = it }, "نرخ سالانه", Icons.Rounded.TrendingUp) }
        item { ChipRow(LoanMethod.entries, method, { it.shortFa }) { method = it } }
        if (method == LoanMethod.GIVEN_INSTALLMENT) item {
            val hint = amount.toLongOrNull()?.let { LoanCatalog.referenceInstallment(preset, it) }
            AmountField(given, { given = it }, if (hint != null) "قسط ماهانه (" + Fa.group(hint) + ")" else "قسط ماهانه", icon = Icons.Rounded.CreditCard)
        }
        item {
            Button({
                error = null
                schedule = try {
                    val p = requireNotNull(amount.toLongOrNull()) { "مبلغ نامعتبر" }
                    val n = requireNotNull(months.toIntOrNull()) { "تعداد اقساط نامعتبر" }
                    val r = rate.toDoubleOrNull() ?: if (method == LoanMethod.GIVEN_INSTALLMENT) 0.0 else throw IllegalArgumentException("نرخ نامعتبر")
                    LoanCalculator.schedule(p, n, r, method, given.toLongOrNull() ?: LoanCatalog.referenceInstallment(preset, p))
                } catch (e: Exception) { error = e.message ?: "ورودی نامعتبر"; null }
            }, Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Rounded.Calculate, null); Spacer(Modifier.width(8.dp)); Text("محاسبه") }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        schedule?.let { s ->
            item {
                AppCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("قسط اول"); Text(Fa.toman(s.installments.first().total), style = MaterialTheme.typography.titleMedium) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("جمع پرداختی"); Text(Fa.toman(s.totalPaid), style = MaterialTheme.typography.titleMedium) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("جمع کارمزد"); Text(Fa.toman(s.totalFee), style = MaterialTheme.typography.titleMedium) }
                }
            }
            items(s.installments.take(36)) { i ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(Fa.digits("${i.no}")); Text(Fa.group(i.total)) }
            }
        }
    }
}
