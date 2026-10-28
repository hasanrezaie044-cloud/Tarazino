package ir.tarazino.data

import ir.tarazino.domain.*

data class GoalRow(val id: String, val name: String, val target: Long, val deadlineEpochDay: Long?, val deposited: Long)
data class Snapshot(
    val accounts: List<Account>, val transactions: List<Transaction>, val ledger: Ledger,
    val budgets: List<BudgetEntity>, val goals: List<GoalRow>, val loans: List<LoanInfo>, val paidInstallments: Map<String, Set<Int>>,
    val cheques: List<ChequeEntity>, val obligations: List<ObligationEntity>,
)

/**
 * پل بین Room و هستهٔ مالی. هر ثبت ابتدا با Ledger (قواعد دامنه) اعتبارسنجی می‌شود و بعد در Room می‌نشیند؛
 * ایندکس یکتا در Room لایهٔ دوم جلوگیری از تکرار است.
 */
class Repository(private val dao: TarazinoDao) {
    suspend fun snapshot(): Snapshot {
        val accounts = dao.accounts().map { Account(it.id, it.name, it.openingBalance) }
        val txs = dao.transactions().map { it.toDomain() }
        val ledger = Ledger()
        accounts.forEach { ledger.addAccount(it) }
        txs.sortedBy { it.epochDay }.forEach { runCatching { ledger.addTransaction(it) } }
        val deposits = dao.deposits().groupBy { it.goalId }
        val goals = dao.goals().map { GoalRow(it.id, it.name, it.target, it.deadlineEpochDay, deposits[it.id].orEmpty().sumOf { d -> d.amount }) }
        val loans = dao.loans().map { LoanInfo(it.id, it.title, it.presetId, it.principal, it.months, it.ratePercent, LoanMethod.valueOf(it.method), it.givenInstallment, it.firstDueEpochDay) }
        val paid = txs.filter { it.type == TxType.LOAN_PAYMENT }.groupBy { it.loanId!! }.mapValues { (_, v) -> v.mapNotNull { it.installmentNo }.toSet() }
        return Snapshot(accounts, txs, ledger, dao.budgets(), goals, loans, paid, dao.cheques(), dao.obligations())
    }

    suspend fun addAccount(a: Account): Boolean {
        Ledger().addAccount(a) // اعتبارسنجی
        return dao.insertAccount(AccountEntity(a.id, a.name, a.openingBalance)) != -1L
    }

    suspend fun addTransaction(t: Transaction): AddResult {
        val s = snapshot()
        if (s.ledger.addTransaction(t) == AddResult.Duplicate) return AddResult.Duplicate // اعتبارسنجی هم همین‌جا انجام می‌شود
        return if (dao.insertTransaction(t.toEntity()) == -1L) AddResult.Duplicate else AddResult.Added
    }

    /** ویرایش با اعتبارسنجی کامل دامنه؛ همهٔ گزارش‌ها از روی تراکنش‌ها دوباره ساخته می‌شوند. */
    suspend fun updateTransaction(t: Transaction) {
        val s = snapshot()
        s.ledger.update(t)
        dao.updateTransaction(t.toEntity())
    }

    suspend fun deleteTransaction(id: String) = dao.deleteTransaction(id)

    suspend fun upsertBudget(b: BudgetEntity) { require(b.monthlyLimit in 1..Money.MAX_AMOUNT && b.warnPercent in 1..100 && b.category.isNotBlank()) { "بودجه نامعتبر است" }; dao.upsertBudget(b) }
    suspend fun deleteBudget(id: String) = dao.deleteBudget(id)

    suspend fun upsertGoal(g: GoalEntity) { Money.requirePositive(g.target); require(g.name.isNotBlank()) { "نام هدف الزامی است" }; dao.upsertGoal(g) }
    suspend fun deleteGoal(id: String) { dao.deleteGoalDeposits(id); dao.deleteGoal(id) }
    suspend fun deposit(d: GoalDepositEntity): Boolean { Money.requirePositive(d.amount); return dao.insertDeposit(d) != -1L }

    suspend fun upsertLoan(l: LoanEntity) {
        LoanCalculator.schedule(l.principal, l.months, l.ratePercent, LoanMethod.valueOf(l.method), l.givenInstallment) // اعتبارسنجی
        dao.upsertLoan(l)
    }
    suspend fun deleteLoan(id: String) = dao.deleteLoan(id)

    /** پرداخت واقعی قسط؛ فقط یک‌بار و با تأیید کاربر در UI. */
    suspend fun payInstallment(loan: LoanInfo, no: Int, accountId: String, epochDay: Long): AddResult {
        val inst = loan.schedule().installments.first { it.no == no }
        return addTransaction(Transaction("loan-${loan.id}-$no", "loan:${loan.id}:$no", TxType.LOAN_PAYMENT, inst.total, accountId, null, epochDay, "قسط وام", loan.title, loan.id, no))
    }

    suspend fun upsertCheque(c: ChequeEntity) { Money.requirePositive(c.amount); dao.upsertCheque(c) }
    suspend fun deleteCheque(id: String) = dao.deleteCheque(id)

    /** پرداخت چک: یک هزینهٔ واقعی (کلید یکتا) و سپس تغییر وضعیت. */
    suspend fun payCheque(c: ChequeEntity, epochDay: Long): AddResult {
        require(c.status == FutureStatus.PENDING.name) { "چک قابل پرداخت نیست" }
        val r = addTransaction(Transaction("cheque-${c.id}", "cheque:${c.id}", TxType.EXPENSE, c.amount, c.accountId, null, epochDay, "چک", c.note))
        dao.upsertCheque(c.copy(status = FutureStatus.PAID.name))
        return r
    }

    suspend fun upsertObligation(o: ObligationEntity) { Money.requirePositive(o.amount); require(o.counterparty.isNotBlank()) { "نام طرف حساب الزامی است" }; dao.upsertObligation(o) }
    suspend fun deleteObligation(id: String) = dao.deleteObligation(id)

    /** دریافت/پرداخت واقعی طلب یا بدهی؛ همین لحظه موجودی تغییر می‌کند، نه هنگام ثبت. */
    suspend fun settleObligation(o: ObligationEntity, accountId: String, epochDay: Long): AddResult {
        require(o.status == FutureStatus.PENDING.name) { "قبلاً تسویه شده" }
        val type = if (o.isReceivable) TxType.INCOME else TxType.EXPENSE
        val r = addTransaction(Transaction("obl-${o.id}", "obl:${o.id}", type, o.amount, accountId, null, epochDay, if (o.isReceivable) "دریافت طلب" else "پرداخت بدهی", o.counterparty))
        dao.upsertObligation(o.copy(status = FutureStatus.PAID.name))
        return r
    }
}

private fun TransactionEntity.toDomain() = Transaction(id, idempotencyKey, TxType.valueOf(type), amount, accountId, toAccountId, epochDay, categoryId, note, loanId, installmentNo)
private fun Transaction.toEntity() = TransactionEntity(id, idempotencyKey, type.name, amount, accountId, toAccountId, epochDay, categoryId, note, loanId, installmentNo)
