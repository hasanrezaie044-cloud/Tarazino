package ir.tarazino.ai

import ir.tarazino.data.Snapshot
import ir.tarazino.domain.*
import ir.tarazino.util.Fa
import ir.tarazino.util.JalaliDate

/**
 * دستیار آفلاین و رایگان: به سؤال‌های رایج مالی با قواعد ثابت و داده‌های واقعی پاسخ می‌دهد؛
 * بدون اینترنت، بدون کلید API و بدون ارسال هیچ داده‌ای. اگر سؤال را نفهمد null برمی‌گرداند.
 */
object OfflineAssistant {
    val QUICK = listOf("این ماه چقدر خرج کردم؟", "بیشترین هزینه‌ام کدام دسته است؟", "موجودی من چقدر است؟", "با ماه قبل مقایسه کن", "قسط بعدی کی است؟", "پیش‌بینی ماه‌های آینده")

    fun answer(question: String, s: Snapshot, today: Long): String? {
        val t = Fa.normalizeDigits(question).replace('ي', 'ی').replace('ك', 'ک')
        fun has(vararg w: String) = w.any { t.contains(it) }
        val j = JalaliDate.fromEpochDay(today)
        return when {
            has("مقایسه") -> compare(s, j)
            has("بیشترین", "کدام دسته", "دسته") && has("خرج", "هزینه", "دسته") -> topCategories(s, j)
            has("پیش‌بینی", "پیش بینی", "آینده", "آخر ماه", "می‌ماند", "میماند") -> forecast(s, j)
            has("قسط", "وام") -> loans(s)
            has("چک") -> cheques(s)
            has("بودجه") -> budgets(s)
            has("هدف", "پس‌انداز", "پس انداز") -> goals(s)
            has("موجودی", "دارم", "حساب") && !has("خرج", "هزینه") -> balances(s)
            has("درآمد") -> income(s, j)
            has("خرج", "هزینه") -> expense(s, j)
            else -> null
        }
    }

    private fun cur(s: Snapshot, j: JalaliDate) = Reports.monthlyStats(s.transactions, j, 1)[0]
    private fun month(j: JalaliDate) = JalaliDate.MONTH_NAMES[j.month - 1]

    private fun expense(s: Snapshot, j: JalaliDate): String {
        val m = cur(s, j)
        return "هزینهٔ ${month(j)}: ${Fa.toman(m.expense)}" + (if (m.loan > 0) "\nاقساط: ${Fa.toman(m.loan)}" else "") +
            "\nدرآمد: ${Fa.toman(m.income)}\nخالص: ${Fa.toman(m.net)}"
    }

    private fun income(s: Snapshot, j: JalaliDate) = "درآمد ${month(j)}: ${Fa.toman(cur(s, j).income)}"

    private fun topCategories(s: Snapshot, j: JalaliDate): String {
        val from = JalaliDate(j.year, j.month, 1).toEpochDay()
        val to = JalaliDate(j.year, j.month, JalaliDate.daysInMonth(j.year, j.month)).toEpochDay()
        val c = Reports.categoryTotals(s.transactions, from, to).take(3)
        return if (c.isEmpty()) "هزینه‌ای در ${month(j)} ثبت نشده" else c.mapIndexed { i, p -> Fa.digits("${i + 1}") + ". " + p.first + ": " + Fa.toman(p.second) }.joinToString("\n")
    }

    private fun compare(s: Snapshot, j: JalaliDate): String {
        val st = Reports.monthlyStats(s.transactions, j, 2)
        val (p, c) = st[0] to st[1]
        fun diff(a: Long, b: Long) = (if (a - b >= 0) "+" else "−") + Fa.group(kotlin.math.abs(a - b))
        return "هزینه: ${Fa.toman(c.expense)} (قبل: ${Fa.toman(p.expense)}، تغییر ${diff(c.expense, p.expense)})\n" +
            "درآمد: ${Fa.toman(c.income)} (قبل: ${Fa.toman(p.income)}، تغییر ${diff(c.income, p.income)})"
    }

    private fun balances(s: Snapshot): String =
        if (s.accounts.isEmpty()) "حسابی ثبت نشده" else "مجموع: ${Fa.toman(s.ledger.totalCash())}\n" + s.accounts.joinToString("\n") { it.name + ": " + Fa.toman(s.ledger.balanceOf(it.id)) }

    private fun loans(s: Snapshot): String {
        if (s.loans.isEmpty()) return "وامی ثبت نشده"
        return s.loans.joinToString("\n") { l ->
            val paid = s.paidInstallments[l.id].orEmpty()
            val n = LoanPlan.nextUnpaid(l, paid)
            if (n == null) l.title + ": تسویه شده"
            else l.title + ": قسط " + Fa.digits("${n.no}") + " — " + Fa.toman(n.total) + " — " + JalaliDate.fromEpochDay(l.dueDay(n.no)).format() + "\nمانده: " + Fa.toman(LoanPlan.remainingDebt(l, paid))
        }
    }

    private fun cheques(s: Snapshot): String {
        val p = s.cheques.filter { it.status == "PENDING" }
        if (p.isEmpty()) return "چک در انتظاری نیست"
        val next = p.minBy { it.dueEpochDay }
        return "${Fa.digits("${p.size}")} چک — جمع: ${Fa.toman(p.sumOf { it.amount })}\nنزدیک‌ترین: ${Fa.toman(next.amount)} — ${JalaliDate.fromEpochDay(next.dueEpochDay).format()}"
    }

    private fun budgets(s: Snapshot): String {
        if (s.budgets.isEmpty()) return "بودجه‌ای تعریف نشده"
        val j = JalaliDate.fromEpochDay(ir.tarazino.util.Today.epochDay())
        val from = JalaliDate(j.year, j.month, 1).toEpochDay()
        val to = JalaliDate(j.year, j.month, JalaliDate.daysInMonth(j.year, j.month)).toEpochDay()
        val spent = Reports.categoryTotals(s.transactions, from, to).toMap()
        return s.budgets.joinToString("\n") { b -> val u = spent[b.category] ?: 0L; b.category + ": " + Fa.group(u) + " از " + Fa.group(b.monthlyLimit) + " (" + Fa.digits("${u * 100 / b.monthlyLimit}") + "٪)" }
    }

    private fun goals(s: Snapshot): String =
        if (s.goals.isEmpty()) "هدفی تعریف نشده" else s.goals.joinToString("\n") { it.name + ": " + Fa.group(it.deposited) + " از " + Fa.group(it.target) + " (" + Fa.digits("${it.deposited * 100 / it.target}") + "٪)" }

    private fun forecast(s: Snapshot, j: JalaliDate): String {
        val recent = Reports.monthlyStats(s.transactions, j, 4).dropLast(1)
        val f = Reports.forecast(s.ledger.totalCash(), recent.map { it.net }, listOf(0, 0, 0))
        val avg = if (recent.isEmpty()) 0L else recent.sumOf { it.net } / recent.size
        return "برآورد (نه قطعی) با میانگین خالص ماهانه ${Fa.toman(avg)}:\n" + f.mapIndexed { i, v -> "ماه " + Fa.digits("${i + 1}") + ": " + Fa.toman(v) }.joinToString("\n")
    }
}
