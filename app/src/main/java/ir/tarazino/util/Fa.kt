package ir.tarazino.util

import java.math.BigDecimal
import java.math.RoundingMode

/** ارقام، قالب‌بندی تومان و تبدیل مبلغ نوشتاری فارسی. بدون وابستگی به AI. */
object Fa {
    private const val THOUSANDS = '٬'

    /** ارقام فارسی/عربی را به لاتین تبدیل می‌کند و جداکنندهٔ هزارگان را حذف می‌کند. */
    fun normalizeDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                '٫' -> sb.append('.')
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace(Regex("(?<=\\d)[,٬،](?=\\d{3}(?!\\d))"), "")
    }

    fun digits(s: String): String = buildString { for (ch in s) append(if (ch in '0'..'9') '۰' + (ch - '0') else ch) }

    fun group(n: Long): String {
        val neg = n < 0
        val s = if (n == Long.MIN_VALUE) n.toString().drop(1) else kotlin.math.abs(n).toString()
        val out = StringBuilder()
        s.forEachIndexed { i, c ->
            if (i > 0 && (s.length - i) % 3 == 0) out.append(THOUSANDS)
            out.append(c)
        }
        return (if (neg) "−" else "") + digits(out.toString())
    }

    /** مثال: ۱۲٬۵۰۰٬۰۰۰ تومان */
    fun toman(n: Long): String = group(n) + " تومان"

    /** فقط ارقام صحیح؛ ورودی کاربر در فیلدها. null اگر نامعتبر. */
    fun parseDigits(s: String): Long? = normalizeDigits(s).trim().filter { it != ' ' }.toLongOrNull()

    private val UNITS = mapOf(
        "صفر" to 0, "یک" to 1, "دو" to 2, "سه" to 3, "چهار" to 4, "پنج" to 5, "شش" to 6, "شیش" to 6, "هفت" to 7, "هشت" to 8, "نه" to 9,
        "ده" to 10, "یازده" to 11, "دوازده" to 12, "سیزده" to 13, "چهارده" to 14, "پانزده" to 15, "شانزده" to 16, "هفده" to 17,
        "هجده" to 18, "نوزده" to 19, "بیست" to 20, "سی" to 30, "چهل" to 40, "پنجاه" to 50, "شصت" to 60, "هفتاد" to 70, "هشتاد" to 80,
        "نود" to 90, "صد" to 100, "یکصد" to 100, "دویست" to 200, "سیصد" to 300, "چهارصد" to 400, "پانصد" to 500, "ششصد" to 600,
        "هفتصد" to 700, "هشتصد" to 800, "نهصد" to 900,
    )
    private val MULTIPLIERS = mapOf("هزار" to 1_000L, "میلیون" to 1_000_000L, "ملیون" to 1_000_000L, "میلیارد" to 1_000_000_000L)
    private val HALF = setOf("نیم", "نیمه")
    private val CURRENCY = setOf("تومان", "تومن", "تومانی", "تومنی", "ریال")

    private fun isNum(t: String) = t in UNITS || t in MULTIPLIERS || t in HALF || t.toBigDecimalOrNull() != null

    data class AmountMatch(val amount: Long, val startToken: Int, val endToken: Int)

    /** تبدیل رشتهٔ مبلغ مثل «سه و نیم میلیون» یا «۳۵۰ هزار تومن» به تومان. */
    fun parseSpokenAmount(text: String): Long? = extractAmounts(text).firstOrNull()?.amount

    fun extractAmounts(text: String): List<AmountMatch> {
        val tokens = normalizeDigits(text).split(Regex("[\\s،؛;:!؟?()]+")).map { it.trim().trimEnd('.') }.filter { it.isNotEmpty() }
        val result = mutableListOf<AmountMatch>()
        var i = 0
        while (i < tokens.size) {
            if (!isNum(tokens[i])) { i++; continue }
            var j = i
            val run = mutableListOf<String>()
            while (j < tokens.size) {
                val t = tokens[j]
                if (isNum(t)) { run.add(t); j++ }
                else if (t == "و" && run.isNotEmpty() && j + 1 < tokens.size && isNum(tokens[j + 1])) j++
                else break
            }
            var rial = false
            var hasCurrency = false
            if (j < tokens.size && tokens[j] in CURRENCY) { hasCurrency = true; rial = tokens[j] == "ریال"; j++ }
            val value = compute(run, rial)
            val hasMultiplier = run.any { it in MULTIPLIERS }
            if (value != null && (hasMultiplier || hasCurrency || value >= 1000)) result.add(AmountMatch(value, i, j - 1))
            i = j
        }
        return result
    }

    private fun compute(run: List<String>, rial: Boolean): Long? {
        var cur = BigDecimal.ZERO
        var half = false
        var total = BigDecimal.ZERO
        for (t in run) {
            val u = UNITS[t]
            val m = MULTIPLIERS[t]
            when {
                u != null -> cur += BigDecimal(u)
                t in HALF -> half = true
                m != null -> {
                    val mult = BigDecimal(m)
                    val base = if (cur.signum() == 0 && !half) BigDecimal.ONE else cur
                    total += base * mult + (if (half) mult.divide(BigDecimal(2)) else BigDecimal.ZERO)
                    cur = BigDecimal.ZERO
                    half = false
                }
                else -> cur += t.toBigDecimalOrNull() ?: return null
            }
        }
        total += cur
        if (rial) total = total.divide(BigDecimal(10))
        return runCatching { total.setScale(0, RoundingMode.HALF_UP).longValueExact() }.getOrNull()
    }
}

/** پیش‌نویس تراکنش از متن آزاد — همیشه نیازمند تأیید صریح کاربر است و چیزی را مستقیم ثبت نمی‌کند. */
data class TxDraft(
    val isIncome: Boolean,
    val amount: Long,
    val category: String,
    val epochDay: Long,
    val dateAssumed: Boolean,
    val sourceText: String,
)

object LocalDrafter {
    private val INCOME_WORDS = listOf("حقوق", "درآمد", "پاداش", "عیدی", "دریافت کردم", "واریز شد")
    private val CATEGORY_RULES = listOf(
        listOf("خونه", "خانه") to "خرید خانه",
        listOf("بنزین", "سوخت") to "بنزین",
        listOf("تاکسی", "اسنپ", "مترو", "اتوبوس") to "حمل‌ونقل",
        listOf("قبض", "برق", "گاز", "اینترنت") to "قبض",
        listOf("دکتر", "دارو", "درمان", "بیمارستان") to "درمان",
        listOf("نون", "میوه", "سوپر", "رستوران", "ناهار", "شام") to "خوراک",
    )

    fun draft(text: String, todayEpochDay: Long): List<TxDraft> {
        val clauses = normalizeForSplit(text).split(Regex("[،؛;\\n]|\\.(?!\\d)|,(?!\\d)")).map { it.trim() }.filter { it.isNotEmpty() }
        val out = mutableListOf<TxDraft>()
        for (clause in clauses) {
            val amounts = Fa.extractAmounts(clause)
            if (amounts.isEmpty()) continue
            val (day, assumed) = resolveDay(clause, todayEpochDay)
            val income = INCOME_WORDS.any { clause.contains(it) }
            val category = CATEGORY_RULES.firstOrNull { (keys, _) -> keys.any { clause.contains(it) } }?.second ?: "سایر"
            for (a in amounts) out.add(TxDraft(income, a.amount, if (income) "درآمد" else category, day, assumed, clause))
        }
        return out
    }

    private fun normalizeForSplit(text: String) = Fa.normalizeDigits(text)

    private fun resolveDay(clause: String, today: Long): Pair<Long, Boolean> = when {
        clause.contains("پریروز") -> today - 2 to false
        clause.contains("دیروز") -> today - 1 to false
        clause.contains("فردا") -> today + 1 to false
        clause.contains("امروز") -> today to false
        else -> today to true
    }
}
