package engine.effect;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.Args;

/**
 * Read-only context one effect activation runs against (§3.5, §7): typed args, actors, and pre-resolved
 * selector targets, no parsing on the hot path. On a combat pass the {@link #actor()}/{@link #victim()}
 * handles may be cross-region (the resolved projectile shooter): positional actor state must come from
 * {@link #actorOrigin()} (ADR-0043), and any per-target live read must be {@code Regions}-guarded (ADR-0042).
 */
public interface EffectCtx {

    double dbl(String name);

    int integer(String name);

    long lng(String name);

    boolean bool(String name);

    String str(String name);

    /**
     * A HANDLE-LIST argument's interned ids, in authored order (a summon's potion loadout) — empty when the arg
     * is absent. Resolved at compile time, so reading it never parses a token.
     */
    default List<Integer> ids(String name) {
        return args().ids(name);
    }

    /**
     * An EXPRESSION-MAP argument's bindings evaluated against this activation's facts: authored name → number,
     * in authored order. EMPTY — and allocation-free — when the argument is absent or has no bindings, so a
     * kind that offers the slot costs the content that never uses it nothing.
     */
    default Map<String, Double> numbers(String name) {
        return Map.of();
    }

    /** The full typed argument bag, for effects that iterate or forward args. */
    Args args();

    /** The player whose ability fired. */
    Player actor();

    /** The combat victim, or {@code null} for non-combat activations. */
    LivingEntity victim();

    /**
     * The entity that dealt damage to the activator on a DEFENSE-side pass, or {@code null} when this activation
     * had no attacker. Like {@link #victim()} this handle may be cross-region (the resolved projectile shooter),
     * so read POSITION from it only through a {@code Regions}-guarded read.
     */
    default LivingEntity attacker() {
        return null;
    }

    /** The relevant block/area location (e.g. an AoE centre), or {@code null}. */
    Location location();

    /**
     * The actor's feet at activation — the ADR-0043 origin snapshot as a fresh Location (x/y/z, yaw/pitch,
     * world) — or {@code null} when uncaptured (kind not flagged via {@code EffectSpec.actorOrigin()}, no
     * actor, or the guarded cross-region capture failed). Fresh per call: hoist out of per-target loops.
     */
    default Location actorOrigin() {
        return null;
    }

    /** The actor's eye point at activation (origin + captured eye height), or {@code null} as {@link #actorOrigin()}. */
    default Location actorOriginEye() {
        return null;
    }

    /**
     * The living entities resolved for the named target slot (declared via
     * {@code EffectSpec.target}). Empty if the selector matched nothing — never null.
     */
    Iterable<LivingEntity> targets(String selectorName);

    /**
     * The LOCATIONS resolved for the named target slot by a block/coordinate selector
     * ({@code @Block}/{@code @Trench}/{@code @Vein}/…, §A). Empty for an entity selector or when nothing
     * matched — never null. A block-mutating effect ({@code SET_BLOCK}/{@code BREAK_BLOCK}) reads this.
     */
    default Iterable<Location> targetLocations(String selectorName) {
        return java.util.List.of();
    }

    /** The activating ability's level (enchants; {@code 0} for other sources). */
    int level();

    /** The defId of the ability this effect belongs to, for op-visible attribution (ADR-0045); {@code -1} for
     *  hand-built contexts. */
    default int sourceDefId() {
        return -1;
    }

    /**
     * The interned cooldown-scope GROUP id of the ability this effect belongs to — the authored {@code group:} —
     * or {@code -1} when it declares none (and for hand-built contexts).
     *
     * <p>This is the identity an effect that ARMS a deferred payload carries into the carrier, so the landing can
     * fire only that feature's {@code IMPACT} abilities (ADR-0074). Deliberately the group and not
     * {@link #sourceDefId()}: a field's arm and its payload are two separate authored bonuses with two defIds,
     * so a defId filter would match nothing at all.
     */
    default int sourceGroup() {
        return -1;
    }

    /**
     * The activator's active soul-gem id, or {@code null} when they are not in soul mode (REMOVE_SOULS).
     * Souls bind to the activator, so this is the actor's gem — not a target's.
     */
    UUID activeGem();
}
