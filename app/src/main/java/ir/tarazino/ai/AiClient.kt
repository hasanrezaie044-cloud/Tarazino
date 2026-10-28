package ir.tarazino.ai

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * کلاینت مستقیم دستگاه ← ارائه‌دهنده (حالت «کلید خودم»). برای هر پروتکل یک adapter جدا.
 * هرگز کلید یا متن پرسش در لاگ نوشته نمی‌شود. تلاش مجدد خودکار ندارد.
 */
class AiClient(private val store: AiSettingsStore) {
    fun chat(provider: ProviderId, messages: List<ChatMessage>, maxTokens: Int = 800): AiResult {
        val cfg = store.config(provider)
        val key = store.readKey(provider)
        if (!cfg.isUsable(key != null)) return AiResult.Error("تنظیمات این ارائه‌دهنده کامل نیست (کلید، آدرس HTTPS و نام مدل).")
        return try {
            when (provider.protocol) {
                Protocol.OPENAI_COMPATIBLE -> openAi(cfg, key!!, messages, maxTokens)
                Protocol.ANTHROPIC -> anthropic(cfg, key!!, messages, maxTokens)
            }
        } catch (e: java.io.IOException) {
            AiResult.Error("ارتباط با سرویس برقرار نشد. اینترنت را بررسی کن.")
        } catch (e: Exception) {
            AiResult.Error("پاسخ نامعتبر از سرویس دریافت شد.")
        }
    }

    private fun openAi(cfg: ProviderConfig, key: String, msgs: List<ChatMessage>, maxTokens: Int): AiResult {
        val body = JSONObject().put("model", cfg.model).put("max_tokens", maxTokens)
            .put("messages", JSONArray(msgs.map { JSONObject().put("role", it.role).put("content", it.content) }))
        val (code, text) = post(cfg.baseUrl.trimEnd('/') + "/chat/completions", body, mapOf("Authorization" to "Bearer $key"))
        if (code !in 200..299) return AiResult.Error(httpMessage(code))
        val out = JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "")
        return if (out.isBlank() || out == "null") AiResult.Error("پاسخ خالی بود؛ دوباره بپرس") else AiResult.Ok(out)
    }

    private fun anthropic(cfg: ProviderConfig, key: String, msgs: List<ChatMessage>, maxTokens: Int): AiResult {
        val system = msgs.filter { it.role == "system" }.joinToString("\n") { it.content }
        val body = JSONObject().put("model", cfg.model).put("max_tokens", maxTokens)
            .put("messages", JSONArray(msgs.filter { it.role != "system" }.map { JSONObject().put("role", it.role).put("content", it.content) }))
        if (system.isNotBlank()) body.put("system", system)
        val (code, text) = post(cfg.baseUrl.trimEnd('/') + "/messages", body, mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"))
        if (code !in 200..299) return AiResult.Error(httpMessage(code))
        val content = JSONObject(text).getJSONArray("content")
        return AiResult.Ok((0 until content.length()).map { content.getJSONObject(it) }.filter { it.optString("type") == "text" }.joinToString("") { it.getString("text") })
    }

    private fun post(url: String, body: JSONObject, headers: Map<String, String>): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            conn.disconnect()
        }
    }

    private fun httpMessage(code: Int) = when (code) {
        401, 403 -> "کلید API نامعتبر است یا دسترسی ندارد."
        402 -> "اعتبار حساب این سرویس تمام شده است."
        429 -> "سقف درخواست یا سهمیه پر شده است. بعداً دوباره تلاش کن."
        in 500..599 -> "سرویس موقتاً در دسترس نیست."
        else -> "درخواست ناموفق بود (کد $code)."
    }
}
