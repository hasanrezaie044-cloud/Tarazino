package ir.tarazino

import ir.tarazino.domain.*
import org.junit.Assert.*
import org.junit.Test

class LedgerTest {
    private var n = 0
    private fun tx(type: TxType, amount: Long, acc: String, to: String? = null, key: String = "k${n++}", loan: String? = null, no: Int? = null) =
        Transaction("t$n", key, type, amount, acc, to, 20000, loanId = loan, installmentNo = no)

    private fun ledger(a: Long = 10_000_000, b: Long = 0) = Ledger().apply {
        addAccount(Account("A", "نقد", a)); addAccount(Account("B", "بانک", b))
    }

    @Test fun finalBalanceFromOpeningIncomeExpense() {
        val l = ledger()
        l.addTransaction(tx(TxType.INCOME, 5_000_000, "A"))
        l.addTransaction(tx(TxType.EXPENSE, 2_000_000, "A"))
        assertEquals(13_000_000, l.balanceOf("A"))
    }

    @Test fun openingBalanceIsNotIncome() {
        assertEquals(0, ledger().incomeTotal())
    }

    @Test fun transferKeepsTotalCashAndIsNotIncomeOrExpense() {
        val l = ledger()
        l.addTransaction(tx(TxType.TRANSFER, 2_000_000, "A", "B"))
        assertEquals(8_000_000, l.balanceOf("A")); assertEquals(2_000_000, l.balanceOf("B"))
        assertEquals(10_000_000, l.totalCash()); assertEquals(0, l.incomeTotal()); assertEquals(0, l.expenseTotal())
    }

    @Test fun installmentIsDeductedOnlyOnce() {
        val l = ledger()
        assertEquals(AddResult.Added, l.addTransaction(tx(TxType.LOAN_PAYMENT, 1_000_000, "A", loan = "L1", no = 1)))
        assertEquals(AddResult.Duplicate, l.addTransaction(tx(TxType.LOAN_PAYMENT, 1_000_000, "A", loan = "L1", no = 1)))
        assertEquals(9_000_000, l.balanceOf("A"))
    }

    @Test fun futureChequeDoesNotReduceCashUntilPaid_andPayingTwiceDeductsOnce() {
        val l = ledger()
        val cheque = Cheque("c1", 3_000_000, 20010, "A")
        assertEquals(10_000_000, l.balanceOf("A"))
        val (paid, r1) = l.payCheque(cheque, "tc1")
        val (_, r2) = l.payCheque(paid, "tc2")
        assertEquals(AddResult.Added, r1); assertEquals(AddResult.Duplicate, r2)
        assertEquals(7_000_000, l.balanceOf("A"))
    }

    @Test fun receivableChangesCashOnlyWhenReceived() {
        val l = ledger()
        Obligation("o1", "طرف حساب", 4_000_000, isReceivable = true, dueEpochDay = null)
        assertEquals(10_000_000, l.totalCash())
        l.addTransaction(tx(TxType.INCOME, 4_000_000, "A"))
        assertEquals(14_000_000, l.totalCash())
    }

    @Test fun editAndDeleteUpdateBalancesAndTotals() {
        val l = ledger()
        val e = tx(TxType.EXPENSE, 1_000_000, "A", key = "edit-me")
        l.addTransaction(e)
        l.update(e.copy(amount = 400_000))
        assertEquals(9_600_000, l.balanceOf("A")); assertEquals(400_000, l.expenseTotal())
        l.delete(e.id)
        assertEquals(10_000_000, l.balanceOf("A")); assertEquals(0, l.expenseTotal())
    }

    @Test fun duplicateIdempotencyKeyIsIgnored() {
        val l = ledger()
        l.addTransaction(tx(TxType.EXPENSE, 100, "A", key = "same"))
        assertEquals(AddResult.Duplicate, l.addTransaction(tx(TxType.EXPENSE, 100, "A", key = "same")))
        assertEquals(9_999_900, l.balanceOf("A"))
    }

    @Test fun invalidInputsAreRejected() {
        val l = ledger()
        assertThrows(IllegalArgumentException::class.java) { l.addTransaction(tx(TxType.EXPENSE, -5, "A")) }
        assertThrows(IllegalArgumentException::class.java) { l.addTransaction(tx(TxType.EXPENSE, 0, "A")) }
        assertThrows(IllegalArgumentException::class.java) { l.addTransaction(tx(TxType.EXPENSE, 5, "ZZZ")) }
        assertThrows(IllegalArgumentException::class.java) { l.addTransaction(tx(TxType.TRANSFER, 5, "A", "A")) }
        assertThrows(IllegalArgumentException::class.java) { l.addTransaction(tx(TxType.EXPENSE, Long.MAX_VALUE, "A")) }
    }
}
