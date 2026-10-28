package ir.tarazino

import ir.tarazino.domain.*
import org.junit.Assert.*
import org.junit.Test

class LoanTest {
    @Test fun annuityMatchesReferenceValues() {
        val s = LoanCalculator.schedule(100_000_000, 12, 23.0, LoanMethod.ANNUITY)
        assertEquals(9_407_632L, s.installments.first().total)
        assertEquals(1_916_667L, s.installments.first().fee)
        assertEquals(9_407_635L, s.installments.last().total)
        assertEquals(12_891_587L, s.totalFee)
        assertEquals(0L, s.installments.last().remainingPrincipal)
        assertEquals(100_000_000L, s.installments.sumOf { it.principal })
    }

    @Test fun annuityOfMarriageLoanDoesNotEqualPublishedInstallment() {
        // سند محدودیت: رقم منتشرشدهٔ ۲٬۷۲۷٬۰۰۰ با اقساط مساوی ۴٪ سالانه (۳٬۰۳۷٬۳۵۴) یکی نیست.
        val s = LoanCalculator.schedule(300_000_000, 120, 4.0, LoanMethod.ANNUITY)
        assertEquals(3_037_354L, s.installments.first().total)
    }

    @Test fun zeroRateSplitsEvenly() {
        val s = LoanCalculator.schedule(10_000_000, 10, 0.0, LoanMethod.ANNUITY)
        assertTrue(s.installments.all { it.total == 1_000_000L }); assertEquals(0L, s.totalFee)
    }

    @Test fun simpleFlat() {
        val s = LoanCalculator.schedule(100_000_000, 12, 23.0, LoanMethod.SIMPLE_FLAT)
        assertEquals(23_000_000L, s.totalFee); assertEquals(123_000_000L, s.totalPaid)
    }

    @Test fun equalPrincipalDeclines() {
        val s = LoanCalculator.schedule(12_000_000, 12, 12.0, LoanMethod.EQUAL_PRINCIPAL)
        assertEquals(1_120_000L, s.installments.first().total); assertEquals(1_010_000L, s.installments.last().total)
        assertEquals(12_000_000L, s.installments.sumOf { it.principal })
    }

    @Test fun givenInstallmentIsExact() {
        val s = LoanCalculator.schedule(300_000_000, 120, 4.0, LoanMethod.GIVEN_INSTALLMENT, 2_727_000)
        assertTrue(s.installments.all { it.total == 2_727_000L })
        assertEquals(327_240_000L, s.totalPaid); assertEquals(27_240_000L, s.totalFee)
        assertEquals(0L, s.installments.last().remainingPrincipal)
        assertThrows(IllegalArgumentException::class.java) { LoanCalculator.schedule(300_000_000, 120, 4.0, LoanMethod.GIVEN_INSTALLMENT, 1_000) }
    }

    @Test fun lateFeeUsesRatePlusSixPercent() {
        assertEquals(24_658L, LoanCalculator.lateFee(3_000_000, 4.0, 30))
    }

    @Test fun catalogReferenceInstallments() {
        val m = LoanCatalog.byId("marriage")!!
        assertEquals(2_727_273L, LoanCatalog.referenceInstallment(m, 300_000_000))
        assertEquals(3_181_818L, LoanCatalog.referenceInstallment(m, 350_000_000))
        assertNull(LoanCatalog.referenceInstallment(LoanCatalog.byId("murabaha")!!, 100_000_000))
    }
}
