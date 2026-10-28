package ir.tarazino.util

import android.content.Context
import android.content.SharedPreferences

/** تنظیمات غیرمحرمانهٔ اپ (تم و دسته‌های اعلان). */
object AppPrefs {
    private lateinit var p: SharedPreferences
    fun init(ctx: Context) { if (!::p.isInitialized) p = ctx.applicationContext.getSharedPreferences("tarazino_prefs", Context.MODE_PRIVATE) }
    var theme: String get() = p.getString("theme", "system")!!; set(v) { p.edit().putString("theme", v).apply() }
    var notifyDue: Boolean get() = p.getBoolean("n_due", true); set(v) { p.edit().putBoolean("n_due", v).apply() }
    var notifyBudget: Boolean get() = p.getBoolean("n_budget", true); set(v) { p.edit().putBoolean("n_budget", v).apply() }
    var notifySaved: Boolean get() = p.getBoolean("n_saved", false); set(v) { p.edit().putBoolean("n_saved", v).apply() }
    fun alreadySent(key: String): Boolean = p.getStringSet("sent", emptySet())!!.contains(key)
    fun markSent(key: String) {
        val cur = p.getStringSet("sent", emptySet())!!.toMutableSet()
        cur.add(key)
        p.edit().putStringSet("sent", cur.toList().takeLast(300).toSet()).apply()
    }
}
