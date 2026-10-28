package ir.tarazino.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.tarazino.ai.*
import ir.tarazino.data.*
import ir.tarazino.domain.*
import ir.tarazino.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class AccountRow(val id: String, val name: String, val balance: Long)
data class BudgetRow(val budget: BudgetEntity, val spent: Long)

data class UiState(
    val accounts: List<AccountRow> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val totalCash: Long = 0,
    val monthIncome: Long = 0,
    val monthExpense: Long = 0,
    val monthLoanPayments: Long = 0,
    val monthTitle: String = "",
    val budgets: List<BudgetRow> = emptyList(),
    val snap: Snapshot? = null,
    val loaded: Boolean = false,
)

data class ChatLine(val fromUser: Boolean, val text: String)

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = Repository(TarazinoDb.get(app).dao())
    private val aiStore = AiSettingsStore(app)
    var state by mutableStateOf(UiState())
        private set
    var message by mutableStateOf<String?>(null)
    var chat by mutableStateOf<List<ChatLine>>(emptyList())
        private set
    var proposals by mutableStateOf<List<TxDraft>>(emptyList())
        private set
    var chatBusy by mutableStateOf(false)
        private set
    private var busy = false

    init { AppPrefs.init(app); refresh() }

    fun refresh() {
        viewModelScope.launch {
            val s = repo.snapshot()
            val j = Today.jalali()
            val from = JalaliDate(j.year, j.month, 1).toEpochDay()
            val to = JalaliDate(j.year, j.month, JalaliDate.daysInMonth(j.year, j.month)).toEpochDay()
            val spent = Reports.categoryTotals(s.transactions, from, to).toMap()
            state = UiState(
                accounts = s.accounts.map { AccountRow(it.id, it.name, s.ledger.balanceOf(it.id)) },
                transactions = s.transactions.sortedByDescending { it.epochDay },
                totalCash = s.ledger.totalCash(),
                monthIncome = s.ledger.incomeTotal(from, to),
                monthExpense = s.ledger.expenseTotal(from, to),
                monthLoanPayments = s.ledger.loanPaymentsTotal(from, to),
                monthTitle = JalaliDate.MONTH_NAMES[j.month - 1] + " " + Fa.digits(j.year.toString()),
                budgets = s.budgets.map { BudgetRow(it, spent[it.category] ?: 0L) },
                snap = s,
                loaded = true,
            )
        }
    }

    private fun guarded(notifySaved: Boolean = false, block: suspend () -> String?) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            message = try { block() } catch (e: IllegalArgumentException) { e.message ?: "ورودی نامعتبر" } catch (e: NoSuchElementException) { "مورد پیدا نشد" } catch (e: ArithmeticException) { "مبلغ بیش‌ازحد بزرگ است" }
            busy = false
            if (notifySaved && AppPrefs.notifySaved) message?.let { Notifier.show(app, Notifier.CH_SAVED, "ترازینو", it, "") }
            refresh()
        }
    }

    private fun amountOf(text: String): Long = Fa.parseDigits(text) ?: Fa.parseSpokenAmount(text) ?: throw IllegalArgumentException("مبلغ نامعتبر است")
    private fun dayOf(text: String): Long = (Today.parseJalali(text) ?: throw IllegalArgumentException("تاریخ شمسی نامعتبر است")).toEpochDay()

    /** موجودی اولیه خالی = صفر. هیچ موجودی فرضی ساخته نمی‌شود. */
    fun addAccount(name: String, openingText: String) = guarded {
        val opening = if (openingText.isBlank()) 0L else amountOf(openingText)
        require(name.isNotBlank()) { "نام حساب الزامی است" }
        repo.addAccount(Account(UUID.randomUUID().toString(), name.trim(), opening))
        "حساب ساخته شد."
    }

    fun addTransaction(key: String, type: TxType, amountText: String, accountId: String?, toAccountId: String?, dateText: String, category: String, note: String) = guarded(true) {
        val amount = amountOf(amountText)
        val t = Transaction(UUID.randomUUID().toString(), key, type, amount, accountId ?: throw IllegalArgumentException("حساب را انتخاب کن"), toAccountId, dayOf(dateText), category.ifBlank { null }, note.trim())
        if (repo.addTransaction(t) == AddResult.Added) "ثبت شد: " + Fa.toman(amount) else "تکراری؛ ثبت نشد"
    }

    fun editTransaction(old: Transaction, amountText: String, dateText: String, category: String, note: String) = guarded {
        repo.updateTransaction(old.copy(amount = amountOf(amountText), epochDay = dayOf(dateText), categoryId = category.ifBlank { null }, note = note.trim()))
        "ویرایش شد"
    }

    /** ثبت پس از تأیید صریح کاربر روی پیش‌نمایش. */
    fun confirmDrafts(batchKey: String, drafts: List<TxDraft>, accountId: String?) = guarded(true) {
        val acc = accountId ?: throw IllegalArgumentException("حساب انتخاب نشده")
        var added = 0; var total = 0L
        drafts.forEachIndexed { i, d ->
            val t = Transaction(UUID.randomUUID().toString(), "draft:$batchKey:$i:${d.amount}", if (d.isIncome) TxType.INCOME else TxType.EXPENSE, d.amount, acc, null, d.epochDay, d.category, d.sourceText)
            if (repo.addTransaction(t) == AddResult.Added) { added++; total += d.amount }
        }
        if (added == 0) "تکراری؛ ثبت نشد" else "$added مورد ثبت شد. مجموع: ${Fa.toman(total)}"
    }

    fun delete(id: String) = guarded { repo.deleteTransaction(id); "حذف شد." }

    // ---- بودجه و اهداف
    fun saveBudget(category: String, limitText: String, warnText: String) = guarded {
        repo.upsertBudget(BudgetEntity(UUID.randomUUID().toString(), category.trim(), amountOf(limitText), (Fa.parseDigits(warnText) ?: 80L).toInt()))
        "بودجه ذخیره شد."
    }
    fun deleteBudget(id: String) = guarded { repo.deleteBudget(id); "حذف شد." }

    fun saveGoal(name: String, targetText: String, deadlineText: String) = guarded {
        val deadline = if (deadlineText.isBlank()) null else dayOf(deadlineText)
        repo.upsertGoal(GoalEntity(UUID.randomUUID().toString(), name.trim(), amountOf(targetText), deadline))
        "هدف ساخته شد."
    }
    fun deleteGoal(id: String) = guarded { repo.deleteGoal(id); "حذف شد." }
    fun depositGoal(key: String, goalId: String, amountText: String) = guarded {
        val ok = repo.deposit(GoalDepositEntity(UUID.randomUUID().toString(), goalId, amountOf(amountText), Today.epochDay(), key))
        if (ok) "ثبت شد" else "تکراری"
    }

    // ---- وام
    fun saveLoan(title: String, presetId: String, principalText: String, monthsText: String, rateText: String, method: LoanMethod, givenText: String, firstDueText: String) = guarded {
        val given = if (givenText.isBlank()) null else amountOf(givenText)
        repo.upsertLoan(LoanEntity(UUID.randomUUID().toString(), title.ifBlank { "وام" }, presetId, amountOf(principalText), (Fa.parseDigits(monthsText) ?: throw IllegalArgumentException("تعداد اقساط نامعتبر")).toInt(),
            Fa.normalizeDigits(rateText).toDoubleOrNull() ?: 0.0, method.name, given, dayOf(firstDueText)))
        "وام ثبت شد"
    }
    fun deleteLoan(id: String) = guarded { repo.deleteLoan(id); "حذف شد" }
    fun payInstallment(loan: LoanInfo, no: Int, accountId: String?) = guarded(true) {
        val r = repo.payInstallment(loan, no, accountId ?: throw IllegalArgumentException("حساب پرداخت را انتخاب کن"), Today.epochDay())
        if (r == AddResult.Added) "قسط $no پرداخت شد." else "قبلاً ثبت شده"
    }

    // ---- چک و طلب/بدهی
    fun saveCheque(amountText: String, dueText: String, accountId: String?, note: String) = guarded {
        repo.upsertCheque(ChequeEntity(UUID.randomUUID().toString(), amountOf(amountText), dayOf(dueText), accountId ?: throw IllegalArgumentException("حساب را انتخاب کن"), FutureStatus.PENDING.name, note.trim()))
        "چک ثبت شد"
    }
    fun payCheque(c: ChequeEntity) = guarded(true) { if (repo.payCheque(c, Today.epochDay()) == AddResult.Added) "چک پرداخت شد." else "قبلاً ثبت شده" }
    fun deleteCheque(id: String) = guarded { repo.deleteCheque(id); "حذف شد." }

    fun saveObligation(counterparty: String, amountText: String, dueText: String, receivable: Boolean, note: String) = guarded {
        repo.upsertObligation(ObligationEntity(UUID.randomUUID().toString(), counterparty.trim(), amountOf(amountText), receivable, if (dueText.isBlank()) null else dayOf(dueText), FutureStatus.PENDING.name, note.trim()))
        "ثبت شد"
    }
    fun settle(o: ObligationEntity, accountId: String?) = guarded(true) {
        if (repo.settleObligation(o, accountId ?: throw IllegalArgumentException("حساب را انتخاب کن"), Today.epochDay()) == AddResult.Added) "تسویه ثبت شد." else "قبلاً تسویه شده بود."
    }
    fun deleteObligation(id: String) = guarded { repo.deleteObligation(id); "حذف شد." }

    // ---- دستیار هوش مصنوعی (فقط خلاصهٔ محاسبه‌شده فرستاده می‌شود)
    fun ask(text: String) {
        if (chatBusy || text.isBlank()) return
        val s = state.snap
        chat = chat + ChatLine(true, text); proposals = emptyList()
        if (s != null) {
            val drafts = LocalDrafter.draft(text, Today.epochDay())
            if (drafts.isNotEmpty()) { proposals = drafts; chat = chat + ChatLine(false, "پیش‌نمایش ثبت"); return }
            OfflineAssistant.answer(text, s, Today.epochDay())?.let { chat = chat + ChatLine(false, it); return }
        }
        if (!aiStore.aiEnabled || !aiStore.hasKey(aiStore.activeProvider)) { chat = chat + ChatLine(false, "پاسخی ندارم"); return }
        chatBusy = true
        viewModelScope.launch {
            val ctx = if (s == null) "داده‌ای نیست." else AssistantTools.buildContext(s, Today.jalali())
            val msgs = listOf(ChatMessage("system", AssistantTools.SYSTEM_PROMPT + "\n\nداده‌های محاسبه‌شده:\n" + ctx)) +
                chat.takeLast(6).map { ChatMessage(if (it.fromUser) "user" else "assistant", it.text) }
            val r = withContext(Dispatchers.IO) { AiClient(aiStore).chat(aiStore.activeProvider, msgs, 700) }
            when (r) {
                is AiResult.Ok -> { chat = chat + ChatLine(false, r.text.replace(Regex("```json.*?```", RegexOption.DOT_MATCHES_ALL), "").trim()); proposals = AssistantTools.parseProposals(r.text, Today.epochDay()) }
                is AiResult.Error -> chat = chat + ChatLine(false, "⚠️ " + r.messageFa)
            }
            chatBusy = false
        }
    }

    fun clearProposals() { proposals = emptyList() }

    fun exportCsv(): String {
        val names = state.accounts.associate { it.id to it.name }
        return "تاریخ,نوع,مبلغ(تومان),حساب,دسته,توضیح\n" + state.transactions.sortedBy { it.epochDay }.joinToString("\n") { t ->
            listOf(JalaliDate.fromEpochDay(t.epochDay).format(), t.type.name, t.amount.toString(), names[t.accountId] ?: "", t.categoryId ?: "", t.note.replace(",", "،").replace("\n", " ")).joinToString(",")
        }
    }

    fun wipeAllLocalData() { viewModelScope.launch { withContext(Dispatchers.IO) { TarazinoDb.get(app).clearAllTables() }; message = "همهٔ داده‌های این دستگاه حذف شد."; refresh() } }
}
