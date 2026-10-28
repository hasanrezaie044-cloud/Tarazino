package ir.tarazino.util

/**
 * تقویم شمسی بر پایهٔ قاعدهٔ حسابی ۳۳ ساله: سال کبیسه است اگر (۲۵·y + ۱۱) mod ۳۳ < ۸.
 * با تاریخ‌های مرجع ۱۳۹۸ تا ۱۴۰۵ و کبیسه‌های ۱۳۹۹/۱۴۰۳ تطبیق داده شده است (docs/TEST_REPORT.md).
 * هشدار: این قاعده برای محدودهٔ حدود ۱۱۷۸ تا ۱۶۳۳ دقیق است؛ مرجع نجومی رسمی نیست.
 * مبنا: روز ۱ فروردین ۱۴۰۵ = ۲۰۲۶-۰۳-۲۱ (epochDay ۲۰۵۳۳).
 */
data class JalaliDate(val year: Int, val month: Int, val day: Int) {
    init {
        require(month in 1..12) { "ماه نامعتبر" }
        require(day in 1..daysInMonth(year, month)) { "روز نامعتبر" }
    }

    fun toEpochDay(): Long = ANCHOR_EPOCH - dayNo(1405, 1, 1) + dayNo(year, month, day)

    /** مثال: ۱۴۰۵/۰۸/۰۲ */
    fun format(): String = Fa.digits("%04d/%02d/%02d".format(year, month, day))

    /** افزودن ماه شمسی؛ اگر روز در ماه مقصد وجود نداشت به آخرین روز همان ماه می‌رود. */
    fun plusMonths(n: Int): JalaliDate {
        val total = year * 12 + (month - 1) + n
        val y = Math.floorDiv(total, 12)
        val m = Math.floorMod(total, 12) + 1
        return JalaliDate(y, m, minOf(day, daysInMonth(y, m)))
    }

    fun formatLong(): String = Fa.digits(day.toString()) + " " + MONTH_NAMES[month - 1] + " " + Fa.digits(year.toString())

    companion object {
        private const val ANCHOR_EPOCH = 20533L
        val MONTH_NAMES = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
        private val MONTH_DAYS = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

        fun isLeap(y: Int): Boolean = Math.floorMod(25 * y + 11, 33) < 8

        fun daysInMonth(y: Int, m: Int): Int = if (m == 12 && isLeap(y)) 30 else MONTH_DAYS[m - 1]

        private fun leapsBefore(y: Int): Long {
            val cycles = Math.floorDiv(y - 1, 33)
            var c = cycles * 8L
            for (k in (cycles * 33 + 1) until y) if (isLeap(k)) c++
            return c
        }

        private fun dayNo(y: Int, m: Int, d: Int): Long {
            var n = 365L * (y - 1) + leapsBefore(y)
            for (i in 0 until m - 1) n += MONTH_DAYS[i]
            return n + d - 1
        }

        fun fromEpochDay(epochDay: Long): JalaliDate {
            val n = epochDay - ANCHOR_EPOCH + dayNo(1405, 1, 1)
            var y = Math.floorDiv(n, 366L).toInt() + 1
            while (dayNo(y + 1, 1, 1) <= n) y++
            while (dayNo(y, 1, 1) > n) y--
            var r = (n - dayNo(y, 1, 1)).toInt()
            var m = 1
            while (r >= daysInMonth(y, m)) { r -= daysInMonth(y, m); m++ }
            return JalaliDate(y, m, r + 1)
        }
    }
}
