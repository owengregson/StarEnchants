package item.render;

/**
 * Roman-numeral formatting for enchant levels (docs/architecture.md §4.2) — the conventional
 * {@code Sharpness V} style. Pure and allocation-light; no Bukkit. Levels outside the classic
 * Roman range (1–3999) fall back to the plain Arabic number, so an absurd level can never throw
 * or render garbage.
 */
public final class Numerals {

    private static final int[] VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    private Numerals() {
    }

    /** {@code value} as an uppercase Roman numeral, or the plain Arabic number if out of range. */
    public static String roman(int value) {
        if (value < 1 || value > 3999) {
            return Integer.toString(value);
        }
        StringBuilder out = new StringBuilder();
        int remaining = value;
        for (int i = 0; i < VALUES.length; i++) {
            while (remaining >= VALUES[i]) {
                out.append(SYMBOLS[i]);
                remaining -= VALUES[i];
            }
        }
        return out.toString();
    }
}
