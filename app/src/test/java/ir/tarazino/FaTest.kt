package ir.tarazino

import ir.tarazino.util.Fa
import ir.tarazino.util.LocalDrafter
import org.junit.Assert.*
import org.junit.Test

class FaTest {
    @Test fun formatsToman() {
        assertEquals("۱۲٬۵۰۰٬۰۰۰ تومان", Fa.toman(12_500_000))
        assertEquals("۰ تومان", Fa.toman(0))
    }

    @Test fun normalizesPersianArabicAndLatinDigits() {
        assertEquals("350", Fa.normalizeDigits("۳۵۰"))
        assertEquals("350", Fa.normalizeDigits("٣٥٠"))
        assertEquals("1200000", Fa.normalizeDigits("۱٬۲۰۰٬۰۰۰"))
        assertEquals(350L, Fa.parseDigits("٣٥٠"))
    }

    @Test fun spokenAmounts() {
        assertEquals(350_000L, Fa.parseSpokenAmount("۳۵۰ هزار تومن"))
        assertEquals(3_500_000L, Fa.parseSpokenAmount("سه و نیم میلیون"))
        assertEquals(350L, Fa.parseSpokenAmount("سیصد و پنجاه تومن"))
        assertEquals(1_200_000L, Fa.parseSpokenAmount("یک میلیون و دویست هزار تومان"))
        assertEquals(1_500_000L, Fa.parseSpokenAmount("1.5 میلیون"))
        assertEquals(1_200_000L, Fa.parseSpokenAmount("۱٬۲۰۰٬۰۰۰ تومان"))
        assertEquals(2_000_000_000L, Fa.parseSpokenAmount("دو میلیارد"))
        assertNull(Fa.parseSpokenAmount("۳ تا نون خریدم"))
    }

    @Test fun twoTransactionsFromOneSentence() {
        val d = LocalDrafter.draft("امروز ۳۵۰ هزار تومن خرید خونه داشتم، ۱۲۰ هزار تومن هم بنزین زدم.", todayEpochDay = 20750)
        assertEquals(2, d.size)
        assertEquals(350_000L, d[0].amount); assertEquals("خرید خانه", d[0].category); assertFalse(d[0].dateAssumed)
        assertEquals(120_000L, d[1].amount); assertEquals("بنزین", d[1].category); assertTrue(d[1].dateAssumed)
        assertTrue(d.none { it.isIncome })
    }

    @Test fun arabicDigitsSentence() {
        val d = LocalDrafter.draft("دیروز ٢٠٠ هزار تومان قبض برق دادم", todayEpochDay = 20750)
        assertEquals(1, d.size); assertEquals(200_000L, d[0].amount); assertEquals(20749L, d[0].epochDay)
    }
}
