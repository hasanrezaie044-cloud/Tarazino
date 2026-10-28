package ir.tarazino.ai

import android.content.Context

/**
 * ذخیرهٔ تنظیمات AI روی دستگاه. کلید API هر ارائه‌دهنده رمزنگاری‌شده است، هرگز لاگ نمی‌شود
 * و در پشتیبان‌گیری ابری/انتقال دستگاه وارد نمی‌شود (data_extraction_rules).
 */
class AiSettingsStore(context: Context, private val box: SecretBox = SecretBox()) {
    private val prefs = context.getSharedPreferences("tarazino_ai", Context.MODE_PRIVATE)

    var activeProvider: ProviderId
        get() = runCatching { ProviderId.valueOf(prefs.getString("active", null) ?: "") }.getOrDefault(ProviderId.OPENROUTER)
        set(v) { prefs.edit().putString("active", v.name).apply() }

    var aiEnabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(v) { prefs.edit().putBoolean("enabled", v).apply() }

    fun config(p: ProviderId): ProviderConfig = ProviderConfig(
        prefs.getString("url_${p.name}", null) ?: p.defaultBaseUrl,
        prefs.getString("model_${p.name}", null) ?: p.defaultModel,
    )

    fun saveConfig(p: ProviderId, c: ProviderConfig) {
        prefs.edit().putString("url_${p.name}", c.baseUrl.trim()).putString("model_${p.name}", c.model.trim()).apply()
    }

    fun hasKey(p: ProviderId) = prefs.contains("key_${p.name}")

    fun saveKey(p: ProviderId, key: String) {
        require(key.isNotBlank()) { "کلید خالی است" }
        prefs.edit().putString("key_${p.name}", box.encrypt(key.trim())).apply()
    }

    internal fun readKey(p: ProviderId): String? = prefs.getString("key_${p.name}", null)?.let { box.decrypt(it) }

    fun deleteKey(p: ProviderId) { prefs.edit().remove("key_${p.name}").apply() }

    /** فقط برای نمایش: چهار نویسهٔ آخر. */
    fun maskedKey(p: ProviderId): String? = readKey(p)?.let { "••••" + it.takeLast(4) }
}
