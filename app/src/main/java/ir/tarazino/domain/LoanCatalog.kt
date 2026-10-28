package ir.tarazino.domain

/** درجهٔ اطمینان داده‌های پیش‌فرض. هیچ‌کدام جایگزین قرارداد یا ابلاغیهٔ روز بانک نیست. */
enum class Confidence(val titleFa: String) {
    PUBLISHED_FIGURE("عدد منتشرشده در منابع خبری/بانکی"),
    NEWS_REPORT("گزارش خبری؛ با ابلاغیه تطبیق داده نشده"),
    USER_INPUT("بدون مقدار پیش‌فرض؛ کاربر باید وارد کند"),
}

data class LoanPreset(
    val id: String,
    val titleFa: String,
    val contractFa: String,
    val defaultRatePercent: Double?,
    val maxAmountToman: Long?,
    val maxMonths: Int,
    val method: LoanMethod,
    /** اگر روش GIVEN_INSTALLMENT است: قسط مرجع به‌ازای هر ۱۰۰ میلیون تومان اصل (منبع: ارقام منتشرشده). */
    val referenceInstallmentPer100M: Long? = null,
    val confidence: Confidence,
    val notesFa: String,
)

/**
 * پیش‌فرض‌های وام‌های ایران (تاریخ بررسی: ۱۴۰۵/۰۸/۰۲). مبلغ و نرخ هر سال با بخشنامهٔ بانک مرکزی تغییر می‌کند؛
 * کاربر می‌تواند همهٔ مقادیر را در صفحهٔ محاسبه ویرایش کند. جزئیات و منابع: docs/IRAN_LOANS.md
 */
object LoanCatalog {
    val presets: List<LoanPreset> = listOf(
        LoanPreset(
            "marriage", "وام ازدواج", "قرض‌الحسنه", 4.0, 300_000_000, 120, LoanMethod.GIVEN_INSTALLMENT,
            referenceInstallmentPer100M = 909_091, confidence = Confidence.PUBLISHED_FIGURE,
            notesFa = "کارمزد ۴٪، ۱۲۰ ماه. ۱۴۰۵: ۳۰۰ میلیون برای هر زوج، مشروط به گذشت حداکثر ۴ سال از ازدواج و عدم دریافت قبلی. " +
                "رقم منتشرشدهٔ قسط (۳۰۰م ← حدود ۲٬۷۲۷٬۰۰۰ و ۳۵۰م ← حدود ۳٬۱۸۱٬۰۰۰) با فرمول اقساط مساوی نمی‌خواند؛ قسط ابلاغی بانک را وارد کن. " +
                "سقف ۳۵۰ میلیون (زیر ۲۵/۲۳ سال) در ۱۴۰۴ بوده و برای ۱۴۰۵ تأیید نشده.",
        ),
        LoanPreset(
            "childbirth", "وام فرزندآوری", "قرض‌الحسنه", 4.0, null, 120, LoanMethod.GIVEN_INSTALLMENT,
            confidence = Confidence.USER_INPUT,
            notesFa = "دستورالعمل ۱۴۰۵ ابلاغ شده؛ مبلغ بسته به رتبهٔ فرزند است و در این نسخه راستی‌آزمایی نشده — مبلغ و قسط را وارد کن.",
        ),
        LoanPreset(
            "housing_qard", "وام مسکن/ودیعه قرض‌الحسنه", "قرض‌الحسنه", 4.0, 400_000_000, 120, LoanMethod.GIVEN_INSTALLMENT,
            confidence = Confidence.NEWS_REPORT,
            notesFa = "گزارش تیر ۱۴۰۵: سقف ۴۰۰ میلیون با نرخ ۴٪ برای خرید/ساخت/ودیعه؛ سقف ودیعهٔ تهران حدود ۳۶۵ میلیون. شرایط خانوار و شهر را از بانک بپرس.",
        ),
        LoanPreset(
            "qard_generic", "قرض‌الحسنهٔ عمومی (معدل حساب و مشابه)", "قرض‌الحسنه", 4.0, null, 60, LoanMethod.GIVEN_INSTALLMENT,
            confidence = Confidence.USER_INPUT,
            notesFa = "کارمزد معمولاً ۰، ۲ یا ۴ درصد (طرح‌های بانکی متفاوت). قسط اعلام‌شده در قرارداد را وارد کن.",
        ),
        LoanPreset(
            "installment_sale", "فروش اقساطی", "فروش اقساطی", 23.0, null, 60, LoanMethod.ANNUITY,
            confidence = Confidence.NEWS_REPORT,
            notesFa = "نرخ سود مصوب حدود ۲۳٪ گزارش شده (برخی طرح‌ها ۱۴ و ۱۸٪). بانک ممکن است سود را ساده یا روی مانده حساب کند؛ روش را از قرارداد تطبیق بده.",
        ),
        LoanPreset(
            "murabaha", "مرابحه", "مرابحه", 23.0, null, 60, LoanMethod.ANNUITY,
            confidence = Confidence.NEWS_REPORT,
            notesFa = "پرسودترین سهم تسهیلات بانکی؛ نرخ و مدت طبق مقررات بانک مرکزی و قرارداد.",
        ),
        LoanPreset(
            "civil_partnership", "مشارکت مدنی", "مشارکت مدنی", 23.0, null, 60, LoanMethod.ANNUITY,
            confidence = Confidence.NEWS_REPORT,
            notesFa = "نرخ سود علی‌الحساب را بانک مرکزی تعیین می‌کند؛ قرارداد را ببین.",
        ),
        LoanPreset("lease_to_own", "اجاره به شرط تملیک", "اجاره به شرط تملیک", null, null, 120, LoanMethod.ANNUITY, confidence = Confidence.USER_INPUT, notesFa = "نرخ و مدت را از قرارداد وارد کن."),
        LoanPreset("jo3ale", "جعاله", "جعاله", null, null, 60, LoanMethod.ANNUITY, confidence = Confidence.USER_INPUT, notesFa = "نرخ طبق آخرین دستورالعمل بانک مرکزی و قرارداد."),
        LoanPreset("mudaraba", "مضاربه / سلف / استصناع", "مضاربه، سلف، استصناع", null, null, 36, LoanMethod.SIMPLE_FLAT, confidence = Confidence.USER_INPUT, notesFa = "عقود تولیدی/تجاری؛ نرخ و روش را از قرارداد وارد کن."),
        LoanPreset("custom", "وام دلخواه", "سفارشی", null, null, 120, LoanMethod.ANNUITY, confidence = Confidence.USER_INPUT, notesFa = "همه‌چیز را خودت تعیین کن."),
    )

    fun byId(id: String): LoanPreset? = presets.firstOrNull { it.id == id }

    /** قسط مرجع برای مبلغ دلخواه با نسبت منتشرشده (فقط وقتی preset آن را دارد). */
    fun referenceInstallment(preset: LoanPreset, principal: Long): Long? =
        preset.referenceInstallmentPer100M?.let { principal * it / 100_000_000L }
}
