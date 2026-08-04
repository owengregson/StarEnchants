package engine.condition;

import java.util.Arrays;
import java.util.function.DoubleSupplier;
import java.util.function.UnaryOperator;

/**
 * A reusable buffer of activation facts as primitives (docs/architecture.md §3.4): numeric, boolean
 * (a {@code long} bitset), and string slots read by compiled slot index, so the hot path does zero string
 * parsing and zero boxing. Slot indices come from the {@link VarVocabulary} the compiler lowered against,
 * so a condition's {@code slot} and this buffer agree by construction. Not thread-safe — one buffer per
 * worker thread, cleared and repopulated per activation via {@link #clear()}.
 */
public final class FactBuffer {

    /** Max boolean flags: two {@code long} bitsets (v3.1 §A). */
    public static final int MAX_FLAGS = 2 * Long.SIZE;

    private final double[] numbers;
    private final String[] strings;
    private long flags0; // flags 0..63
    private long flags1; // flags 64..127
    private UnaryOperator<String> papi = t -> null;
    private UnaryOperator<String> victimVars = t -> null;
    private PotionLevels potions = PotionLevels.NONE;
    private EnchantLevels enchants = EnchantLevels.NONE;
    // rand()'s draw. Defaults to 0 (never an inline ThreadLocalRandom) exactly like Activation's chanceRoll:
    // production installs the real source, so an unwired evaluation is reproducible instead of secretly random.
    private DoubleSupplier random = () -> 0.0;

    public FactBuffer(int numberSlots, int flagSlots, int stringSlots) {
        if (numberSlots < 0 || flagSlots < 0 || stringSlots < 0) {
            throw new IllegalArgumentException("slot counts must be non-negative");
        }
        if (flagSlots > MAX_FLAGS) {
            throw new IllegalArgumentException("at most " + MAX_FLAGS + " flag slots, got " + flagSlots);
        }
        this.numbers = new double[numberSlots];
        this.strings = new String[stringSlots];
    }

    public void setNumber(int slot, double value) {
        numbers[slot] = value;
    }

    public double number(int slot) {
        return numbers[slot];
    }

    public void setFlag(int slot, boolean value) {
        long bit = 1L << (slot & 63);
        if (slot < Long.SIZE) {
            flags0 = value ? (flags0 | bit) : (flags0 & ~bit);
        } else {
            flags1 = value ? (flags1 | bit) : (flags1 & ~bit);
        }
    }

    public boolean flag(int slot) {
        long bit = 1L << (slot & 63);
        return slot < Long.SIZE ? (flags0 & bit) != 0 : (flags1 & bit) != 0;
    }

    public void setString(int slot, String value) {
        strings[slot] = value;
    }

    public String string(int slot) {
        return strings[slot];
    }

    /** Install the per-activation PlaceholderAPI resolver; {@code null} (the default) means "no PAPI". */
    public void papiResolver(UnaryOperator<String> resolver) {
        this.papi = resolver == null ? t -> null : resolver;
    }

    /** Resolve a PAPI token (the {@code %...%} text without the percents); {@code null} if PAPI absent or unknown. */
    public String resolvePapi(String token) {
        return papi.apply(token);
    }

    /** Install the per-activation victim-scoped var reader ({@code %victim.var.<name>%}); {@code null} = none. */
    public void victimVarResolver(UnaryOperator<String> resolver) {
        this.victimVars = resolver == null ? t -> null : resolver;
    }

    /** A victim-scoped dynamic var; {@code null} when there is no victim or the var is unset. */
    public String resolveVictimVar(String name) {
        return victimVars.apply(name);
    }

    /** Install the per-activation potion reader ({@code %scope.potion.<effect>%}); {@code null} = none. */
    public void potionLevels(PotionLevels levels) {
        this.potions = levels == null ? PotionLevels.NONE : levels;
    }

    /** The actor's level of the resolved potion handle ({@code amplifier + 1}); {@code 0} when absent. */
    public int actorPotionLevel(int potionEffectId) {
        return potions.actorLevel(potionEffectId);
    }

    /** The victim's level of the resolved potion handle ({@code amplifier + 1}); {@code 0} when absent. */
    public int victimPotionLevel(int potionEffectId) {
        return potions.victimLevel(potionEffectId);
    }

    /** Install the per-activation worn-enchant reader ({@code %scope.enchlevel.<key>%}); {@code null} = none. */
    public void enchantLevels(EnchantLevels levels) {
        this.enchants = levels == null ? EnchantLevels.NONE : levels;
    }

    /** The actor's worn level of the enchant {@code key}; {@code 0} when not worn. */
    public int actorEnchantLevel(String key) {
        return enchants.actorLevel(key);
    }

    /** The victim's worn level of the enchant {@code key}; {@code 0} when not worn. */
    public int victimEnchantLevel(String key) {
        return enchants.victimLevel(key);
    }

    /** Install the random source {@code rand(lo,hi)} draws from; {@code null} (the default) draws {@code 0}. */
    public void randomSource(DoubleSupplier source) {
        this.random = source == null ? () -> 0.0 : source;
    }

    /** One draw in {@code [0,1)} for {@code rand(lo,hi)}; {@code 0} when no source is installed. */
    public double random() {
        return random.getAsDouble();
    }

    /** Reset all slots; called once per activation for thread-local reuse. */
    public void clear() {
        Arrays.fill(numbers, 0.0);
        Arrays.fill(strings, null);
        flags0 = 0L;
        flags1 = 0L;
        papi = t -> null;
        victimVars = t -> null;
        potions = PotionLevels.NONE;
        enchants = EnchantLevels.NONE;
        random = () -> 0.0;
    }
}
