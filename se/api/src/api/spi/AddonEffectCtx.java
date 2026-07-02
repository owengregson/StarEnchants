package api.spi;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * The read-only context one {@link AddonEffect} activation runs against — the add-on-facing view of the
 * engine's internal {@code EffectCtx} (docs/architecture.md §3.5, §7). Typed argument reads (already parsed
 * and range-checked by the effect's {@link AddonSpec}), the pre-resolved selector targets, and the firing
 * actor. On a combat pass the {@link #actor()}/{@link #victim()} handles may be cross-region (the resolved
 * projectile shooter): positional actor state must come from {@link #actorOrigin()} (ADR-0043), and any live
 * per-target read must be region-guarded.
 *
 * <p>An argument name that the spec did not declare is a programming error, not a silent {@code 0}/{@code
 * null} — the underlying context is strict.
 */
public interface AddonEffectCtx {

    /** The declared {@code DOUBLE} argument {@code name}. */
    double dbl(String name);

    /** The declared {@code INT}/{@code TICKS}/{@code HANDLE} argument {@code name} (a {@code HANDLE} reads as its interned id). */
    int integer(String name);

    /** The declared {@code STRING} argument {@code name}. */
    String str(String name);

    /** The declared {@code BOOL} argument {@code name}. */
    boolean bool(String name);

    /** The living entities resolved for the named target slot; empty if nothing matched — never null. */
    Iterable<LivingEntity> targets(String selectorName);

    /** The activating ability's level (enchants; {@code 0} for level-less sources like crystals/sets). */
    int level();

    /** The player whose ability fired. */
    Player actor();

    /** The combat victim, or {@code null} for a non-combat activation. */
    LivingEntity victim();

    /** The relevant block/area location (e.g. an AoE centre), or {@code null}. */
    Location location();

    /**
     * The actor's feet at activation — the ADR-0043 origin snapshot — or {@code null} when uncaptured (the
     * effect did not declare {@code AddonSpec.actorOrigin()}, no actor, or the guarded capture failed). Fresh
     * per call: hoist out of per-target loops.
     */
    default Location actorOrigin() {
        return null;
    }

    /** The actor's eye point at activation, or {@code null} as {@link #actorOrigin()}. */
    default Location actorOriginEye() {
        return null;
    }
}
