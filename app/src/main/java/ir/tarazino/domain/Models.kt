package ir.tarazino.domain

/** تمام مبلغ‌ها عدد صحیح بر حسب تومان‌اند (بدون Double). */
object Money {
    const val MAX_AMOUNT: Long = 1_000_000_000_000_000L
    fun requirePositive(amount: Long) = require(amount in 1..MAX_AMOUNT) { "مبلغ نامعتبر است" }
}

enum class TxType { INCOME, EXPENSE, TRANSFER, LOAN_PAYMENT }

data class Account(val id: String, val name: String, val openingBalance: Long)

data class Transaction(
    val id: String,
    val idempotencyKey: String,
    val type: TxType,
    val amount: Long,
    val accountId: String,
    val toAccountId: String? = null,
    val epochDay: Long,
    val categoryId: String? = null,
    val note: String = "",
    val loanId: String? = null,
    val installmentNo: Int? = null,
)

enum class FutureStatus { PENDING, PAID, CANCELLED }

/** چک آینده: تا زمان پرداخت واقعی روی موجودی اثر ندارد. */
data class Cheque(val id: String, val amount: Long, val dueEpochDay: Long, val accountId: String, val status: FutureStatus = FutureStatus.PENDING)

/** طلب/بدهی: ثبت آن به‌تنهایی جریان نقدی ایجاد نمی‌کند. */
data class Obligation(val id: String, val counterparty: String, val amount: Long, val isReceivable: Boolean, val dueEpochDay: Long?, val status: FutureStatus = FutureStatus.PENDING)
