package api.spi;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * The read-only context one {@link AddonEffect} activation runs against — the add-on-facing view of the
 * engine's internal {@code EffectCtx} (docs/architecture.md §3.5, §7). Typed argument reads (already parsed
 * and range-checked by the effect's {@link AddonSpec}), the pre-resolved selector targets, and the firing
 * actor. Everything reachable here is the firing-thread actor or a snapshot-safe value; an add-on never
 * touches a live cross-region entity itself.
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
}
