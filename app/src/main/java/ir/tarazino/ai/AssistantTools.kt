package ir.tarazino.ai

import ir.tarazino.data.Snapshot
import ir.tarazino.domain.*
import ir.tarazino.util.*
import org.json.JSONObject

/**
 * ابزارهای محدود و قطعی: همهٔ اعداد از پایگاه داده محاسبه می‌شوند و مدل فقط متن خلاصه را می‌بیند.
 * مدل SQL، دسترسی مستقیم به دیتابیس یا ثبت خودکار تراکنش ندارد؛ فقط می‌تواند پیش‌نویس پیشنهاد بدهد.
 * حریم خصوصی: نام حساب‌ها، توضیح تراکنش‌ها، نام اشخاص و شمارهٔ چک هرگز فرستاده نمی‌شوند.
 */
object AssistantTools {
    fun getMonthlySummary(s: Snapshot, j: JalaliDate): String {
        val st = Reports.monthlyStats(s.transactions, j, 1)[0]
        return "ماه ${JalaliDate.MONTH_NAMES[j.month - 1]} ${j.year}: درآمد ${st.income}، هزینه ${st.expense}، اقساط ${st.loan}، خالص ${st.net} تومان."
    }

    fun getExpenseCategories(s: Snapshot, j: JalaliDate): String {
        val from = JalaliDate(j.year, j.month, 1).toEpochDay()
        val to = JalaliDate(j.year, j.month, JalaliDate.daysInMonth(j.year, j.month)).toEpochDay()
        val c = Reports.categoryTotals(s.transactions, from, to).take(8)
        return if (c.isEmpty()) "هزینه‌ای در این ماه ثبت نشده." else "هزینه به تفکیک دسته (تومان): " + c.joinToString("، ") { "${it.first}=${it.second}" }
    }

    fun compareMonths(s: Snapshot, j: JalaliDate): String {
        val st = Reports.monthlyStats(s.transactions, j, 3)
        return "سه ماه اخیر (درآمد/هزینه/اقساط): " + st.joinToString("؛ ") { "${it.month}: ${it.income}/${it.expense}/${it.loan}" }
    }

    fun forecast(s: Snapshot, j: JalaliDate): String {
        val st = Reports.monthlyStats(s.transactions, j, 4).dropLast(1)
        val f = Reports.forecast(s.ledger.totalCash(), st.map { it.net }, listOf(0, 0, 0))
        return "موجودی نقدی فعلی ${s.ledger.totalCash()} تومان. برآورد (نه قطعی) با فرض تکرار میانگین خالص سه ماه گذشته و بدون تعهدات: ${f.joinToString("، ")}."
    }

    fun buildContext(s: Snapshot, j: JalaliDate): String =
        listOf(getMonthlySummary(s, j), getExpenseCategories(s, j), compareMonths(s, j), forecast(s, j),
            "تعداد چک‌های در انتظار: ${s.cheques.count { it.status == "PENDING" }}؛ جمع آن‌ها ${s.cheques.filter { it.status == "PENDING" }.sumOf { it.amount }} تومان.").joinToString("\n")

    const val SYSTEM_PROMPT = "تو دستیار مالی اپ ترازینو هستی و فارسی پاسخ می‌دهی. فقط از «داده‌های محاسبه‌شده» زیر استفاده کن و هیچ عددی را حدس نزن؛ " +
        "اگر داده کافی نیست بگو. تو نمی‌توانی چیزی ثبت کنی. اگر کاربر خواست تراکنشی ثبت شود، فقط در پایان پاسخ یک بلوک ```json با قالب " +
        "{\"transactions\":[{\"type\":\"expense|income\",\"amount\":عدد_صحیح_تومان,\"category\":\"متن\"}]} بده؛ کاربر باید تأیید کند. اگر مبلغ یا نوع مبهم است به‌جای JSON سؤال بپرس."

    /** پیشنهاد مدل را سخت‌گیرانه اعتبارسنجی می‌کند؛ خروجی نامعتبر نادیده گرفته می‌شود. */
    fun parseProposals(reply: String, todayEpochDay: Long): List<TxDraft> {
        val m = Regex("```json\\s*(\\{.*?\\})\\s*```", RegexOption.DOT_MATCHES_ALL).find(reply) ?: return emptyList()
        return runCatching {
            val arr = JSONObject(m.groupValues[1]).getJSONArray("transactions")
            (0 until minOf(arr.length(), 10)).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val type = o.getString("type").lowercase()
                val amount = o.getLong("amount")
                if (type !in setOf("income", "expense") || amount !in 1..Money.MAX_AMOUNT) null
                else TxDraft(type == "income", amount, o.optString("category", "سایر").take(40).ifBlank { "سایر" }, todayEpochDay, true, "پیشنهاد دستیار")
            }
        }.getOrDefault(emptyList())
    }
}
