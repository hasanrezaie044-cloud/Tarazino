package ir.tarazino.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ir.tarazino.domain.*
import ir.tarazino.util.*

private data class Series(val key: String, val label: String, val color: Color, val values: List<Long>)

/** همهٔ گزارش‌ها در یک نمودار تعاملی: بازهٔ زمانی، نمایش/پنهان‌کردن شاخص‌ها، فیلتر دسته، لمس و کشیدن برای مقدار دقیق. */
@Composable
fun ReportsScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val snap = vm.state.snap
    var months by remember { mutableIntStateOf(6) }
    var active by remember { mutableStateOf(setOf("income", "expense", "net")) }
    var category by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableIntStateOf(-1) }
    val j = Today.jalali()

    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle("گزارش", Icons.Rounded.Insights)
        if (snap == null || snap.transactions.isEmpty()) { EmptyState(Icons.Rounded.Insights, "داده‌ای نیست"); return@Column }

        val stats = Reports.monthlyStats(snap.transactions, j, months)
        val from = JalaliDate(stats.first().year, stats.first().month, 1).toEpochDay()
        val to = JalaliDate(j.year, j.month, JalaliDate.daysInMonth(j.year, j.month)).toEpochDay()
        val cats = Reports.categoryTotals(snap.transactions, from, to).map { it.first }.take(8)
        val catSeries = category?.let { c ->
            stats.map { s ->
                val f = JalaliDate(s.year, s.month, 1).toEpochDay(); val t = JalaliDate(s.year, s.month, JalaliDate.daysInMonth(s.year, s.month)).toEpochDay()
                snap.transactions.filter { it.type == TxType.EXPENSE && (it.categoryId ?: "بدون دسته") == c && it.epochDay in f..t }.sumOf { it.amount }
            }
        }
        val all = listOf(
            Series("income", "درآمد", Income, stats.map { it.income }),
            Series("expense", category ?: "هزینه", Expense, catSeries ?: stats.map { it.expense }),
            Series("loan", "اقساط", Color(0xFFF59E0B), stats.map { it.loan }),
            Series("net", "خالص", Accent, stats.map { it.net }),
            Series("balance", "موجودی", Color(0xFF8B5CF6), Reports.balanceTrend(snap.accounts.sumOf { it.openingBalance }, snap.transactions, stats)),
        )
        val shown = all.filter { it.key in active }
        val idx = selected.coerceIn(-1, stats.size - 1)

        ChipRow(listOf(3, 6, 12), months, { Fa.digits("$it") + " ماه" }, { Icons.Rounded.Event }) { months = it; selected = -1 }

        AppCard {
            if (shown.isEmpty()) EmptyState(Icons.Rounded.Insights, "شاخصی انتخاب نشده")
            else InteractiveChart(shown, stats.map { Fa.digits("${it.month}") }, idx) { selected = it }
            if (idx >= 0) {
                Text(JalaliDate.MONTH_NAMES[stats[idx].month - 1] + " " + Fa.digits("${stats[idx].year}"), style = MaterialTheme.typography.titleMedium)
                shown.forEach { s ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(10.dp).background(s.color, CircleShape))
                        Text(s.label, Modifier.weight(1f))
                        Text(Fa.toman(s.values[idx]), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(all) { s ->
                FilterChip(
                    selected = s.key in active, onClick = { active = if (s.key in active) active - s.key else active + s.key }, shape = CircleShape,
                    label = { Text(s.label) }, leadingIcon = { Box(Modifier.size(10.dp).background(s.color, CircleShape)) },
                )
            }
        }
        if (cats.isNotEmpty()) ChipRow(listOf<String?>(null) + cats, category, { it ?: "همه دسته‌ها" }, { Icons.Rounded.PieChart }) { category = it }
    }
}

@Composable
private fun InteractiveChart(series: List<Series>, labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val n = labels.size
    val minV = minOf(0L, series.minOf { it.values.min() })
    val maxV = maxOf(1L, series.maxOf { it.values.max() })
    val span = (maxV - minV).toFloat()
    val grid = MaterialTheme.colorScheme.outline
    val sel = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Fa.group(maxV), style = MaterialTheme.typography.labelSmall, color = sel)
            Text(Fa.group(minV), style = MaterialTheme.typography.labelSmall, color = sel)
        }
        Canvas(
            Modifier.fillMaxWidth().height(200.dp)
                .pointerInput(n) { detectTapGestures { o -> onSelect(((o.x / size.width) * (n - 1) + 0.5f).toInt().coerceIn(0, n - 1)) } }
                .pointerInput(n) { detectHorizontalDragGestures { c, _ -> onSelect(((c.position.x / size.width) * (n - 1) + 0.5f).toInt().coerceIn(0, n - 1)) } },
        ) {
            fun x(i: Int) = if (n == 1) size.width / 2 else size.width * i / (n - 1)
            fun y(v: Long) = size.height - size.height * (v - minV) / span
            for (g in 0..4) drawLine(grid.copy(alpha = 0.5f), Offset(0f, size.height * g / 4), Offset(size.width, size.height * g / 4), 1f)
            if (minV < 0) drawLine(sel, Offset(0f, y(0)), Offset(size.width, y(0)), 2f)
            if (selected >= 0) drawLine(sel, Offset(x(selected), 0f), Offset(x(selected), size.height), 2f)
            series.forEach { s ->
                val path = Path()
                s.values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
                drawPath(path, s.color, style = Stroke(5f, cap = StrokeCap.Round))
                s.values.forEachIndexed { i, v -> drawCircle(s.color, if (i == selected) 12f else 6f, Offset(x(i), y(v))) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = sel) } }
    }
}
