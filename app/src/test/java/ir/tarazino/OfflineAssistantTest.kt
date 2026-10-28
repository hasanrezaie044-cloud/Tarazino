package ir.tarazino

import ir.tarazino.ai.OfflineAssistant
import ir.tarazino.data.Snapshot
import ir.tarazino.domain.*
import ir.tarazino.util.Fa
import ir.tarazino.util.JalaliDate
import org.junit.Assert.*
import org.junit.Test

class OfflineAssistantTest {
    private val today = JalaliDate(1405, 8, 2).toEpochDay()
    private val snap: Snapshot = run {
        val acc = Account("A", "نقد", 1_000_000)
        val l = Ledger().apply { addAccount(acc) }
        val txs = listOf(
            Transaction("1", "k1", TxType.INCOME, 5_000_000, "A", epochDay = JalaliDate(1405, 8, 1).toEpochDay()),
            Transaction("2", "k2", TxType.EXPENSE, 300_000, "A", epochDay = JalaliDate(1405, 8, 1).toEpochDay(), categoryId = "بنزین"),
        )
        txs.forEach { l.addTransaction(it) }
        Snapshot(listOf(acc), txs, l, emptyList(), emptyList(), emptyList(), emptyMap(), emptyList(), emptyList())
    }

    @Test fun answersExpenseBalanceAndTopCategory() {
        assertTrue(OfflineAssistant.answer("این ماه چقدر خرج کردم؟", snap, today)!!.contains(Fa.group(300_000)))
        assertTrue(OfflineAssistant.answer("موجودی من چقدر است؟", snap, today)!!.contains(Fa.group(5_700_000)))
        assertTrue(OfflineAssistant.answer("بیشترین هزینه‌ام کدام دسته است؟", snap, today)!!.contains("بنزین"))
    }

    @Test fun unknownQuestionReturnsNull() {
        assertNull(OfflineAssistant.answer("سلام", snap, today))
    }

    @Test fun everyQuickQuestionIsUnderstood() {
        OfflineAssistant.QUICK.forEach { assertNotNull(it, OfflineAssistant.answer(it, snap, today)) }
    }
}
