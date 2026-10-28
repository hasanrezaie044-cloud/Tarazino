package ir.tarazino.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** روش‌های محاسبهٔ قسط. جزئیات و منابع: docs/IRAN_LOANS.md */
enum class LoanMethod(val titleFa: String, val shortFa: String) {
    ANNUITY("اقساط مساوی با سود/کارمزد روی مانده (فرمول استاندارد)", "اقساط مساوی"),
    SIMPLE_FLAT("سود ساده: کل سود = اصل × نرخ × سال، تقسیم مساوی", "سود ساده"),
    EQUAL_PRINCIPAL("اصل مساوی + سود روی مانده (قسط نزولی)", "قسط نزولی"),
    GIVEN_INSTALLMENT("قسط ثابتِ اعلام‌شده توسط بانک/قرارداد", "قسط قرارداد"),
}

data class Installment(val no: Int, val principal: Long, val fee: Long, val total: Long, val remainingPrincipal: Long)

data class LoanSchedule(val installments: List<Installment>) {
    val totalPaid: Long get() = installments.sumOf { it.total }
    val totalFee: Long get() = installments.sumOf { it.fee }
}

object LoanCalculator {
    private val MC = MathContext(40)
    private fun round(x: BigDecimal): Long = x.setScale(0, RoundingMode.HALF_UP).longValueExact()

    fun schedule(
        principal: Long,
        months: Int,
        annualRatePercent: Double,
        method: LoanMethod,
        givenInstallment: Long? = null,
    ): LoanSchedule {
        Money.requirePositive(principal)
        require(months in 1..600) { "تعداد اقساط نامعتبر است" }
        require(annualRatePercent in 0.0..100.0) { "نرخ نامعتبر است" }
        val r = BigDecimal.valueOf(annualRatePercent).divide(BigDecimal(1200), MC)
        return LoanSchedule(
            when (method) {
                LoanMethod.ANNUITY -> annuity(principal, months, r)
                LoanMethod.SIMPLE_FLAT -> flat(principal, months, annualRatePercent)
                LoanMethod.EQUAL_PRINCIPAL -> equalPrincipal(principal, months, r)
                LoanMethod.GIVEN_INSTALLMENT -> given(principal, months, requireNotNull(givenInstallment) { "مبلغ قسط الزامی است" })
            }
        )
    }

    fun annuityPayment(principal: Long, months: Int, r: BigDecimal): Long {
        if (r.signum() == 0) return principal / months
        val f = BigDecimal.ONE.add(r).pow(months, MC)
        return round(BigDecimal(principal).multiply(r, MC).multiply(f, MC).divide(f.subtract(BigDecimal.ONE), MC))
    }

    private fun annuity(p: Long, n: Int, r: BigDecimal): List<Installment> {
        val pmt = annuityPayment(p, n, r)
        var bal = p
        return (1..n).map { k ->
            val interest = round(BigDecimal(bal).multiply(r, MC))
            val pr = if (k < n) (pmt - interest).coerceIn(0L, bal) else bal
            bal -= pr
            Installment(k, pr, interest, pr + interest, bal)
        }
    }

    private fun flat(p: Long, n: Int, rate: Double): List<Installment> {
        val totalFee = round(BigDecimal(p).multiply(BigDecimal.valueOf(rate)).multiply(BigDecimal(n)).divide(BigDecimal(1200), MC))
        var bal = p
        var feeLeft = totalFee
        return (1..n).map { k ->
            val pr = if (k < n) p / n else bal
            val fee = if (k < n) totalFee / n else feeLeft
            bal -= pr
            feeLeft -= fee
            Installment(k, pr, fee, pr + fee, bal)
        }
    }

    private fun equalPrincipal(p: Long, n: Int, r: BigDecimal): List<Installment> {
        var bal = p
        return (1..n).map { k ->
            val pr = if (k < n) p / n else bal
            val fee = round(BigDecimal(bal).multiply(r, MC))
            bal -= pr
            Installment(k, pr, fee, pr + fee, bal)
        }
    }

    /** همهٔ اقساط دقیقاً برابر مبلغ اعلام‌شده‌اند؛ مابه‌التفاوت از اصل، کارمزد محسوب می‌شود. */
    private fun given(p: Long, n: Int, a: Long): List<Installment> {
        Money.requirePositive(a)
        require(a.toBigInteger() * n.toBigInteger() >= p.toBigInteger()) { "مجموع اقساط کمتر از اصل وام است" }
        var bal = p
        val base = p / n
        val extra = (p % n).toInt()
        return (1..n).map { k ->
            val pr = base + if (k <= extra) 1 else 0
            bal -= pr
            Installment(k, pr, a - pr, a, bal)
        }
    }

    /**
     * جریمهٔ دیرکرد: طبق گزارش‌های منتشرشده «نرخ سود وام + ۶ درصد» به‌صورت سالانه روی مبلغ معوق.
     * مبنا ۳۶۵ روز؛ درصد اضافه قابل تنظیم است و باید با قرارداد بانک تطبیق داده شود.
     */
    fun lateFee(overdueAmount: Long, annualRatePercent: Double, days: Int, extraPercent: Double = 6.0): Long {
        require(overdueAmount >= 0 && days >= 0)
        return round(
            BigDecimal(overdueAmount)
                .multiply(BigDecimal.valueOf(annualRatePercent + extraPercent))
                .multiply(BigDecimal(days))
                .divide(BigDecimal(36500), MC)
        )
    }
}
