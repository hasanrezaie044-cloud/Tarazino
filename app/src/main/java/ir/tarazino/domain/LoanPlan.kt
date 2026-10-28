package ir.tarazino.domain

import ir.tarazino.util.JalaliDate

data class LoanInfo(
    val id: String, val title: String, val presetId: String, val principal: Long, val months: Int,
    val ratePercent: Double, val method: LoanMethod, val givenInstallment: Long?, val firstDueEpochDay: Long,
) {
    fun schedule(): LoanSchedule = LoanCalculator.schedule(principal, months, ratePercent, method, givenInstallment)

    /** سررسید قسط k: همان روز شمسیِ اولین قسط، k−۱ ماه بعد. */
    fun dueDay(no: Int): Long = JalaliDate.fromEpochDay(firstDueEpochDay).plusMonths(no - 1).toEpochDay()
}

object LoanPlan {
    /** نخستین قسط پرداخت‌نشده یا null اگر همه پرداخت شده‌اند. */
    fun nextUnpaid(loan: LoanInfo, paid: Set<Int>): Installment? = loan.schedule().installments.firstOrNull { it.no !in paid }

    fun remainingDebt(loan: LoanInfo, paid: Set<Int>): Long = loan.schedule().installments.filter { it.no !in paid }.sumOf { it.total }
}
