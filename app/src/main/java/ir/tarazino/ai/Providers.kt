package ir.tarazino.ai

/** ارائه‌دهندگان پشتیبانی‌شده. نام مدل و آدرس‌ها قابل ویرایش‌اند و جای دیگری در کد ثابت نشده‌اند. */
enum class ProviderId(val titleFa: String, val defaultBaseUrl: String, val defaultModel: String, val protocol: Protocol) {
    OPENROUTER("OpenRouter (رایگان)", "https://openrouter.ai/api/v1", "openrouter/free", Protocol.OPENAI_COMPATIBLE),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com", "deepseek-flash", Protocol.OPENAI_COMPATIBLE),
    OPENAI("OpenAI", "https://api.openai.com/v1", "", Protocol.OPENAI_COMPATIBLE),
    CLAUDE("Claude (Anthropic)", "https://api.anthropic.com/v1", "", Protocol.ANTHROPIC),
    GROK("Grok (xAI)", "https://api.x.ai/v1", "", Protocol.OPENAI_COMPATIBLE),
    GEMINI("Gemini (Google AI Studio)", "https://generativelanguage.googleapis.com/v1beta/openai", "", Protocol.OPENAI_COMPATIBLE),
    CUSTOM("سازگار با OpenAI (دلخواه)", "", "", Protocol.OPENAI_COMPATIBLE),
}

enum class Protocol { OPENAI_COMPATIBLE, ANTHROPIC }

/** تنظیمات غیرمحرمانهٔ هر ارائه‌دهنده. کلید API جداگانه و رمزنگاری‌شده نگهداری می‌شود. */
data class ProviderConfig(val baseUrl: String, val model: String) {
    fun isUsable(hasKey: Boolean): Boolean = hasKey && baseUrl.startsWith("https://") && model.isNotBlank()
}

data class ChatMessage(val role: String, val content: String)

sealed interface AiResult {
    data class Ok(val text: String) : AiResult
    data class Error(val messageFa: String) : AiResult
}
