package engine.spec;

/**
 * Built-in target-selector names an {@link EffectSpec} can declare via
 * {@link EffectSpec.Builder#target(String, String)} (docs/architecture.md §7). Plain {@code String}
 * constants, not a closed enum, because selectors are pluggable: an add-on registers a new selector
 * and declares it by name without touching this list.
 */
public final class T {

    private T() {
    }

    /** The activating player themself. */
    public static final String SELF = "SELF";

    /** The combat victim (defender on the attacker's hit). */
    public static final String VICTIM = "VICTIM";

    /** The attacker (the entity that dealt damage to the activator). */
    public static final String ATTACKER = "ATTACKER";

    /** Every living entity in an area around the primary target. */
    public static final String AOE = "AOE";

    /** The single nearest living entity. */
    public static final String NEAREST = "NEAREST";

    /** The activation's own block LOCATION (location analogue of {@link #SELF}); default target of block effects, resolved via {@code @Here}. */
    public static final String HERE = "HERE";
}
