package item.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NumeralsTest {

    @Test
    void rendersClassicRomanNumerals() {
        assertEquals("I", Numerals.roman(1));
        assertEquals("III", Numerals.roman(3));
        assertEquals("IV", Numerals.roman(4));
        assertEquals("IX", Numerals.roman(9));
        assertEquals("XL", Numerals.roman(40));
        assertEquals("LVIII", Numerals.roman(58));
        assertEquals("MCMXCIV", Numerals.roman(1994));
    }

    @Test
    void fallsBackToArabicOutsideTheClassicRange() {
        assertEquals("0", Numerals.roman(0));
        assertEquals("-2", Numerals.roman(-2));
        assertEquals("4000", Numerals.roman(4000));
    }
}
