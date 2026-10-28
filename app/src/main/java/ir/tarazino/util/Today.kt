package ir.tarazino.util

import java.util.TimeZone

/** روز جاری به‌وقت تهران، بر حسب epochDay. */
object Today {
    fun epochDay(nowMillis: Long = System.currentTimeMillis()): Long {
        val tz = TimeZone.getTimeZone("Asia/Tehran")
        return Math.floorDiv(nowMillis + tz.getOffset(nowMillis), 86_400_000L)
    }
    fun jalali(): JalaliDate = JalaliDate.fromEpochDay(epochDay())

    /** ورودی مثل ۱۴۰۵/۰۸/۰۲ یا 1405-8-2؛ null اگر نامعتبر. */
    fun parseJalali(text: String): JalaliDate? {
        val p = Fa.normalizeDigits(text).trim().split('/', '-', '.').map { it.trim().toIntOrNull() ?: return null }
        if (p.size != 3) return null
        return runCatching { JalaliDate(p[0], p[1], p[2]) }.getOrNull()
    }
}
