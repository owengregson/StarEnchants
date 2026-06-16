package engine.spec;

/**
 * The built-in target-selector names an {@link EffectSpec} can declare via
 * {@link EffectSpec.Builder#target(String, String)} (docs/architecture.md §7,
 * "{@code .target("who", T.AOE)}"). These mirror the {@code SelectorKind}s the
 * engine resolves at compile + equip time; an effect names which target slot(s) it
 * reads, and the resolved entities arrive via {@code EffectCtx.targets(name)}.
 *
 * <p>These are plain {@code String} constants, not a closed enum, because selectors
 * are a pluggable kind: an add-on may register a new selector and declare it by name
 * without this list changing.
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
}
