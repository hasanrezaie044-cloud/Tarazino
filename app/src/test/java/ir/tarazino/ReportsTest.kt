package ir.tarazino

import ir.tarazino.domain.*
import ir.tarazino.util.JalaliDate
import org.junit.Assert.*
import org.junit.Test

class ReportsTest {
    private fun t(type: TxType, amt: Long, d: JalaliDate, cat: String? = null) =
        Transaction("${d.toEpochDay()}$type$amt$cat", "k${d.toEpochDay()}$type$amt$cat", type, amt, "A", if (type == TxType.TRANSFER) "B" else null, d.toEpochDay(), cat)

    @Test fun monthlyStatsIgnoreTransfers() {
        val txs = listOf(
            t(TxType.INCOME, 5_000_000, JalaliDate(1405, 7, 5)), t(TxType.EXPENSE, 1_000_000, JalaliDate(1405, 7, 9), "خوراک"),
            t(TxType.TRANSFER, 9_000_000, JalaliDate(1405, 7, 10)), t(TxType.LOAN_PAYMENT, 500_000, JalaliDate(1405, 8, 1)),
        )
        val s = Reports.monthlyStats(txs, JalaliDate(1405, 8, 2), 2)
        assertEquals(listOf(7, 8), s.map { it.month })
        assertEquals(5_000_000L, s[0].income); assertEquals(1_000_000L, s[0].expense); assertEquals(0L, s[0].loan)
        assertEquals(500_000L, s[1].loan); assertEquals(-500_000L, s[1].net)
    }

    @Test fun monthlyStatsCrossYear() {
        val s = Reports.monthlyStats(emptyList(), JalaliDate(1405, 2, 1), 4)
        assertEquals(listOf(11, 12, 1, 2), s.map { it.month }); assertEquals(listOf(1404, 1404, 1405, 1405), s.map { it.year })
    }

    @Test fun categoryTotalsAndTrendAndForecast() {
        val txs = listOf(t(TxType.EXPENSE, 300, JalaliDate(1405, 8, 1), "الف"), t(TxType.EXPENSE, 700, JalaliDate(1405, 8, 2), "ب"), t(TxType.INCOME, 2000, JalaliDate(1405, 7, 1)))
        assertEquals(listOf("ب" to 700L, "الف" to 300L), Reports.categoryTotals(txs, JalaliDate(1405, 8, 1).toEpochDay(), JalaliDate(1405, 8, 30).toEpochDay()))
        val stats = Reports.monthlyStats(txs, JalaliDate(1405, 8, 2), 2)
        assertEquals(listOf(10_000L + 2000, 10_000L + 1000), Reports.balanceTrend(10_000, txs, stats))
        assertEquals(listOf(1_100L, 1_200L), Reports.forecast(1_000, listOf(100, 100), listOf(0, 0)))
    }

    @Test fun jalaliPlusMonthsClampsDay() {
        assertEquals(JalaliDate(1405, 12, 29), JalaliDate(1405, 6, 31).plusMonths(6))
        assertEquals(JalaliDate(1406, 1, 15), JalaliDate(1405, 12, 15).plusMonths(1))
    }
}
