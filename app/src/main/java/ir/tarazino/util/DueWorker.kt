package ir.tarazino.util

import android.content.Context
import androidx.work.*
import ir.tarazino.data.Repository
import ir.tarazino.data.TarazinoDb
import ir.tarazino.domain.*
import java.util.concurrent.TimeUnit

/**
 * بررسی روزانهٔ سررسیدها با WorkManager (بدون زمان‌بندی دقیق؛ سیستم‌عامل و Doze ممکن است اجرا را چند ساعت جابه‌جا کنند).
 * WorkManager پس از ریبوت خودش دوباره زمان‌بندی می‌شود؛ نیازی به BOOT receiver نیست.
 */
class DueWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        AppPrefs.init(applicationContext)
        val s = Repository(TarazinoDb.get(applicationContext).dao()).snapshot()
        val today = Today.epochDay()
        val soon = today + 1
        if (AppPrefs.notifyDue) {
            s.cheques.filter { it.status == "PENDING" && it.dueEpochDay <= soon }.forEach {
                Notifier.show(applicationContext, Notifier.CH_DUE, "سررسید چک", Fa.toman(it.amount) + " تا " + JalaliDate.fromEpochDay(it.dueEpochDay).format(), "cheque:${it.id}:$today")
            }
            s.obligations.filter { it.status == "PENDING" && (it.dueEpochDay ?: Long.MAX_VALUE) <= soon }.forEach {
                Notifier.show(applicationContext, Notifier.CH_DUE, if (it.isReceivable) "موعد دریافت طلب" else "موعد پرداخت بدهی", it.counterparty + ": " + Fa.toman(it.amount), "obl:${it.id}:$today")
            }
            s.loans.forEach { l ->
                val next = LoanPlan.nextUnpaid(l, s.paidInstallments[l.id].orEmpty()) ?: return@forEach
                if (l.dueDay(next.no) <= soon) Notifier.show(applicationContext, Notifier.CH_DUE, "سررسید قسط", l.title + ": " + Fa.toman(next.total), "loan:${l.id}:${next.no}:$today")
            }
        }
        if (AppPrefs.notifyBudget) {
            val j = JalaliDate.fromEpochDay(today)
            val from = JalaliDate(j.year, j.month, 1).toEpochDay()
            val to = JalaliDate(j.year, j.month, JalaliDate.daysInMonth(j.year, j.month)).toEpochDay()
            val spent = Reports.categoryTotals(s.transactions, from, to).toMap()
            s.budgets.forEach { b ->
                val used = spent[b.category] ?: 0L
                if (used * 100 >= b.monthlyLimit * b.warnPercent) Notifier.show(applicationContext, Notifier.CH_BUDGET, "بودجهٔ " + b.category, "مصرف ${Fa.toman(used)} از ${Fa.toman(b.monthlyLimit)}", "budget:${b.id}:${j.year}${j.month}:${b.warnPercent}")
            }
        }
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<DueWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("tarazino-due", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
