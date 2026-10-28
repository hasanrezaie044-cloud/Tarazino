package ir.tarazino.domain

import ir.tarazino.util.JalaliDate

data class MonthStat(val year: Int, val month: Int, val income: Long, val expense: Long, val loan: Long) {
    val net: Long get() = income - expense - loan
}

/** گزارش‌ها فقط از تراکنش‌های واقعی ساخته می‌شوند؛ انتقال در هیچ جمعی نمی‌آید. */
object Reports {
    fun monthlyStats(txs: List<Transaction>, end: JalaliDate, months: Int): List<MonthStat> {
        val ym = mutableListOf<Pair<Int, Int>>()
        var y = end.year; var m = end.month
        repeat(months) { ym.add(0, y to m); m--; if (m == 0) { m = 12; y-- } }
        return ym.map { (yy, mm) ->
            val from = JalaliDate(yy, mm, 1).toEpochDay()
            val to = JalaliDate(yy, mm, JalaliDate.daysInMonth(yy, mm)).toEpochDay()
            val r = txs.filter { it.epochDay in from..to }
            fun s(t: TxType) = r.filter { it.type == t }.sumOf { it.amount }
            MonthStat(yy, mm, s(TxType.INCOME), s(TxType.EXPENSE), s(TxType.LOAN_PAYMENT))
        }
    }

    fun categoryTotals(txs: List<Transaction>, from: Long, to: Long): List<Pair<String, Long>> =
        txs.filter { it.type == TxType.EXPENSE && it.epochDay in from..to }
            .groupBy { it.categoryId ?: "بدون دسته" }.map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second }

    private fun cashDelta(t: Transaction): Long = when (t.type) {
        TxType.INCOME -> t.amount; TxType.EXPENSE, TxType.LOAN_PAYMENT -> -t.amount; TxType.TRANSFER -> 0
    }

    /** موجودی نقدی کل در پایان هر ماهِ بازه. */
    fun balanceTrend(openingTotal: Long, txs: List<Transaction>, stats: List<MonthStat>): List<Long> =
        stats.map { s ->
            val end = JalaliDate(s.year, s.month, JalaliDate.daysInMonth(s.year, s.month)).toEpochDay()
            openingTotal + txs.filter { it.epochDay <= end }.sumOf { cashDelta(it) }
        }

    /**
     * پیش‌بینی مشروط (برآورد، نه داده): موجودی فعلی + میانگین خالص ماه‌های اخیر در هر ماه − تعهدات زمان‌بندی‌شدهٔ همان ماه.
     * فرض‌ها باید کنار نتیجه نمایش داده شوند.
     */
    fun forecast(current: Long, recentNets: List<Long>, scheduledOutflows: List<Long>): List<Long> {
        val avg = if (recentNets.isEmpty()) 0L else recentNets.sum() / recentNets.size
        var bal = current
        return scheduledOutflows.map { out -> bal += avg - out; bal }
    }
}
