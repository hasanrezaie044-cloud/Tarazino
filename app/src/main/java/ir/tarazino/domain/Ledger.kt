package ir.tarazino.domain

sealed interface AddResult {
    data object Added : AddResult
    /** درخواست تکراری (همان idempotencyKey یا همان قسط) — دوباره ثبت نشد. */
    data object Duplicate : AddResult
}

/**
 * موتور مالی قطعی (بدون AI). موجودی همیشه از «موجودی اولیه + تراکنش‌های واقعی» محاسبه می‌شود.
 * چک آینده، طلب و بدهی در موجودی نقدی نقشی ندارند مگر هنگام پرداخت/دریافت واقعی که یک تراکنش ثبت می‌شود.
 */
class Ledger {
    private val accounts = LinkedHashMap<String, Account>()
    private val txs = LinkedHashMap<String, Transaction>()
    private val keyIndex = HashMap<String, String>()
    private val installmentIndex = HashMap<Pair<String, Int>, String>()

    fun addAccount(a: Account) {
        require(a.openingBalance in 0..Money.MAX_AMOUNT) { "موجودی اولیه نامعتبر است" }
        require(a.id !in accounts) { "حساب تکراری است" }
        accounts[a.id] = a
    }

    fun addTransaction(t: Transaction): AddResult {
        validate(t)
        if (t.idempotencyKey in keyIndex) return AddResult.Duplicate
        if (t.type == TxType.LOAN_PAYMENT) {
            val k = t.loanId!! to t.installmentNo!!
            if (k in installmentIndex) return AddResult.Duplicate
            installmentIndex[k] = t.id
        }
        require(t.id !in txs) { "شناسهٔ تراکنش تکراری است" }
        txs[t.id] = t
        keyIndex[t.idempotencyKey] = t.id
        return AddResult.Added
    }

    fun update(t: Transaction) {
        val old = requireNotNull(txs[t.id]) { "تراکنش وجود ندارد" }
        validate(t)
        require(t.idempotencyKey == old.idempotencyKey) { "کلید یکتا قابل تغییر نیست" }
        txs[t.id] = t
    }

    fun delete(id: String) {
        val t = txs.remove(id) ?: return
        keyIndex.remove(t.idempotencyKey)
        if (t.type == TxType.LOAN_PAYMENT) installmentIndex.remove(t.loanId!! to t.installmentNo!!)
    }

    fun balanceOf(accountId: String): Long {
        val acc = requireNotNull(accounts[accountId]) { "حساب وجود ندارد" }
        var b = acc.openingBalance
        for (t in txs.values) b = Math.addExact(b, delta(t, accountId))
        return b
    }

    fun totalCash(): Long = accounts.keys.fold(0L) { s, id -> Math.addExact(s, balanceOf(id)) }

    /** درآمد واقعی؛ موجودی اولیه و انتقال شامل نمی‌شوند. */
    fun incomeTotal(fromDay: Long = Long.MIN_VALUE, toDay: Long = Long.MAX_VALUE) = sum(TxType.INCOME, fromDay, toDay)
    fun expenseTotal(fromDay: Long = Long.MIN_VALUE, toDay: Long = Long.MAX_VALUE) = sum(TxType.EXPENSE, fromDay, toDay)
    fun loanPaymentsTotal(fromDay: Long = Long.MIN_VALUE, toDay: Long = Long.MAX_VALUE) = sum(TxType.LOAN_PAYMENT, fromDay, toDay)

    /** پرداخت واقعی چک؛ حتی با تکرار درخواست فقط یک‌بار یک هزینه ثبت می‌شود. */
    fun payCheque(c: Cheque, txId: String): Pair<Cheque, AddResult> {
        require(c.status != FutureStatus.CANCELLED) { "چک باطل شده است" }
        val r = addTransaction(Transaction(txId, "cheque:${c.id}", TxType.EXPENSE, c.amount, c.accountId, epochDay = c.dueEpochDay, note = "پرداخت چک"))
        return c.copy(status = FutureStatus.PAID) to r
    }

    private fun sum(type: TxType, from: Long, to: Long): Long =
        txs.values.filter { it.type == type && it.epochDay in from..to }.fold(0L) { s, t -> Math.addExact(s, t.amount) }

    private fun delta(t: Transaction, accountId: String): Long = when (t.type) {
        TxType.INCOME -> if (t.accountId == accountId) t.amount else 0
        TxType.EXPENSE, TxType.LOAN_PAYMENT -> if (t.accountId == accountId) -t.amount else 0
        TxType.TRANSFER -> when (accountId) { t.accountId -> -t.amount; t.toAccountId -> t.amount; else -> 0 }
    }

    private fun validate(t: Transaction) {
        Money.requirePositive(t.amount)
        require(t.accountId in accounts) { "حساب وجود ندارد" }
        require(t.idempotencyKey.isNotBlank()) { "کلید یکتا الزامی است" }
        when (t.type) {
            TxType.TRANSFER -> {
                require(t.toAccountId != null && t.toAccountId in accounts) { "حساب مقصد نامعتبر است" }
                require(t.toAccountId != t.accountId) { "مبدأ و مقصد یکسان است" }
            }
            TxType.LOAN_PAYMENT -> {
                require(t.loanId != null && (t.installmentNo ?: 0) >= 1) { "اطلاعات قسط ناقص است" }
                require(t.toAccountId == null)
            }
            else -> require(t.toAccountId == null) { "حساب مقصد فقط برای انتقال است" }
        }
    }
}
