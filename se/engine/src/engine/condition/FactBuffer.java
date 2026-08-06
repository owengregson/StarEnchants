package engine.condition;

import java.util.Arrays;
import java.util.UUID;
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
    private CrystalCounts crystals = CrystalCounts.NONE;
    // rand()'s draw. Defaults to 0 (never an inline ThreadLocalRandom) exactly like Activation's chanceRoll:
    // production installs the real source, so an unwired evaluation is reproducible instead of secretly random.
    private DoubleSupplier random = () -> 0.0;
    // ── the subject cursor (%target.*%, ADR-0076) ──
    // Primitive/reference FIELDS re-pointed per body, never an object allocated per body: binding one target
    // is a UUID write, a double write and a reference write. Unbound (subject == null) is the state every
    // ordinary effect sees, and every subject read then answers its zero value.
    private UUID subject;
    private double subjectRoll;
    private SubjectBody subjectBody = SubjectBody.NONE;
    private SubjectStores subjectStores = SubjectStores.NONE;

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

    /** How many numeric slots this buffer was sized for — a writer outside the populator must bounds-check
     *  against it, since a synthetic activation's buffer is sized 0 while the vocabulary's slot ids are not. */
    public int numberSlots() {
        return numbers.length;
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

    /** Install the per-activation worn-crystal reader ({@code %scope.crystals.<key>%}); {@code null} = none. */
    public void crystalCounts(CrystalCounts counts) {
        this.crystals = counts == null ? CrystalCounts.NONE : counts;
    }

    /** How many of the actor's worn armour pieces carry the crystal {@code key}; {@code 0} when none. */
    public int actorCrystalCount(String key) {
        return crystals.actorCount(key);
    }

    /** How many of the victim's worn armour pieces carry the crystal {@code key}; {@code 0} when none. */
    public int victimCrystalCount(String key) {
        return crystals.victimCount(key);
    }

    /** Install the random source {@code rand(lo,hi)} draws from; {@code null} (the default) draws {@code 0}. */
    public void randomSource(DoubleSupplier source) {
        this.random = source == null ? () -> 0.0 : source;
    }

    /** One draw in {@code [0,1)} for {@code rand(lo,hi)}; {@code 0} when no source is installed. */
    public double random() {
        return random.getAsDouble();
    }

    /** Install the UUID-keyed subject readers; done once per activation by the populator, never per body. */
    public void subjectStores(SubjectStores stores) {
        this.subjectStores = stores == null ? SubjectStores.NONE : stores;
    }

    /**
     * Point the subject cursor at one resolved target: its id, the body-derived reads, and the ONE uniform
     * {@code [0,100)} draw every {@code each-*} read of this body shares (ADR-0076). A {@code null} id unbinds.
     */
    public void bindSubject(UUID id, SubjectBody body, double roll) {
        this.subject = id;
        this.subjectBody = body == null ? SubjectBody.NONE : body;
        this.subjectRoll = roll;
    }

    /** Unbind the cursor, so a later effect's expression can never read a stale body's facts. */
    public void clearSubject() {
        this.subject = null;
        this.subjectBody = SubjectBody.NONE;
        this.subjectRoll = 0.0;
    }

    /** The bound subject's id, or {@code null} when no cursor is bound. */
    public UUID subject() {
        return subject;
    }

    /** {@code %target.enchlevel.<key>%} — {@code 0} with no cursor bound. */
    public int targetEnchantLevel(String key) {
        return subject == null ? 0 : enchants.levelOf(subject, key);
    }

    /** {@code %target.crystals.<key>%} — {@code 0} with no cursor bound. */
    public int targetCrystalCount(String key) {
        return subject == null ? 0 : crystals.countOf(subject, key);
    }

    /** {@code %target.var.<name>%} — {@code null} with no cursor bound or the var unset. */
    public String resolveTargetVar(String name) {
        return subject == null ? null : subjectStores.var(subject, name);
    }

    /** {@code %target.souls%} — {@code 0} with no cursor bound. */
    public double targetSouls() {
        return subject == null ? 0 : subjectStores.souls(subject);
    }

    /** {@code %target.heroicpieces%} — {@code 0} with no cursor bound. */
    public int targetHeroicPieces() {
        return subject == null ? 0 : subjectStores.heroicPieces(subject);
    }

    /** {@code %target.type%} — empty with no cursor bound. */
    public String targetType() {
        return subjectBody.type();
    }

    /** {@code %target.relation%} — empty with no cursor bound. */
    public String targetRelation() {
        return subjectBody.relation();
    }

    /** {@code %target.roll%} — the bound body's shared draw; {@code 0} with no cursor bound. */
    public double targetRoll() {
        return subjectRoll;
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
        crystals = CrystalCounts.NONE;
        random = () -> 0.0;
        subjectStores = SubjectStores.NONE;
        clearSubject();
    }
}
