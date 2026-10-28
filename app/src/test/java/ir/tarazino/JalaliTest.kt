package ir.tarazino

import ir.tarazino.util.JalaliDate
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class JalaliTest {
    private fun g(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    @Test fun knownNowruzDates() {
        assertEquals(g(2019, 3, 21), JalaliDate(1398, 1, 1).toEpochDay())
        assertEquals(g(2020, 3, 20), JalaliDate(1399, 1, 1).toEpochDay())
        assertEquals(g(2021, 3, 21), JalaliDate(1400, 1, 1).toEpochDay())
        assertEquals(g(2022, 3, 21), JalaliDate(1401, 1, 1).toEpochDay())
        assertEquals(g(2023, 3, 21), JalaliDate(1402, 1, 1).toEpochDay())
        assertEquals(g(2024, 3, 20), JalaliDate(1403, 1, 1).toEpochDay())
        assertEquals(g(2025, 3, 21), JalaliDate(1404, 1, 1).toEpochDay())
        assertEquals(g(2026, 3, 21), JalaliDate(1405, 1, 1).toEpochDay())
    }

    @Test fun leapDayAndToday() {
        assertEquals(g(2025, 3, 20), JalaliDate(1403, 12, 30).toEpochDay())
        assertEquals(g(2026, 10, 24), JalaliDate(1405, 8, 2).toEpochDay())
        assertEquals(JalaliDate(1405, 8, 2), JalaliDate.fromEpochDay(g(2026, 10, 24)))
    }

    @Test fun leapYears() {
        assertTrue(JalaliDate.isLeap(1399)); assertTrue(JalaliDate.isLeap(1403)); assertTrue(JalaliDate.isLeap(1408))
        assertFalse(JalaliDate.isLeap(1404)); assertFalse(JalaliDate.isLeap(1405))
        assertThrows(IllegalArgumentException::class.java) { JalaliDate(1405, 12, 30) }
    }

    @Test fun roundTripAcrossManyYears() {
        var d = g(1950, 1, 1)
        val end = g(2100, 1, 1)
        while (d < end) {
            assertEquals(d, JalaliDate.fromEpochDay(d).toEpochDay()); d += 7
        }
    }

    @Test fun formatsWithPersianDigits() {
        assertEquals("۱۴۰۵/۰۸/۰۲", JalaliDate(1405, 8, 2).format())
        assertEquals("۲ آبان ۱۴۰۵", JalaliDate(1405, 8, 2).formatLong())
    }

    @Test fun parsesUserDateInput() {
        assertEquals(JalaliDate(1405, 8, 2), ir.tarazino.util.Today.parseJalali("۱۴۰۵/۰۸/۰۲"))
        assertEquals(JalaliDate(1405, 8, 2), ir.tarazino.util.Today.parseJalali("1405-8-2"))
        assertNull(ir.tarazino.util.Today.parseJalali("1405/13/1"))
        assertNull(ir.tarazino.util.Today.parseJalali("سلام"))
    }

    @Test fun tehranDayBoundary() {
        // 2026-10-24 21:00 UTC = 2026-10-25 00:30 تهران (UTC+3:30)
        val utc = java.time.LocalDate.of(2026, 10, 24).toEpochDay() * 86_400_000L + 21 * 3_600_000L
        assertEquals(java.time.LocalDate.of(2026, 10, 25).toEpochDay(), ir.tarazino.util.Today.epochDay(utc))
    }
}
