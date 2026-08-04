package feature.compat;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.ProjectileHitEvent;

/**
 * The version edge for projectile-type predicates in combat trigger routing (ADR-0044;
 * docs/legacy-1.8.9-codeshare-design.md §4): {@code Trident} (1.13) and {@code AbstractArrow} (1.14) do not
 * exist on 1.8.9, where the only arrow type is {@code Arrow}. The {@code instanceof} cannot be expressed shared
 * without string compares on a hit path, so two era-exclusive impls back it — {@code ModernProjectiles}
 * ({@code overlay/modern}) and {@code LegacyProjectiles} ({@code overlay/legacy}) — injected into the combat router.
 */
public interface Projectiles {

    /** The {@code %projectilekind%} vocabulary — declared here so both era impls and their tests share one spelling. */
    String ARROW = "ARROW";

    String FIREBALL = "FIREBALL";

    String THROWN = "THROWN";

    String OTHER = "OTHER";

    boolean isTrident(Entity entity);

    /** Any arrow-family projectile (tipped/spectral/normal) — the BOW-trigger family. */
    boolean isArrowLike(Entity entity);

    /**
     * The {@code %projectilekind%} bucket for a projectile damager: {@link #ARROW}, {@link #FIREBALL},
     * {@link #THROWN} (snowball/egg/pearl/potion/xp bottle) or {@link #OTHER}. Callers pass a projectile;
     * anything else is {@link #OTHER}.
     */
    String kindOf(Entity entity);

    /**
     * Hands {@code land} the point where a {@link ProjectileHitEvent}'s projectile came down, or calls it not at
     * all — an entity hit (which BOW/ATTACK already dispatches, so PROJECTILE_LAND must not double-dispatch it),
     * or a projectile this era cannot follow to the ground.
     *
     * <p>A callback rather than a return value because the two eras answer at different TIMES, both verified
     * against the shipped jars. Modern reads {@code getHitEntity}/{@code getHitBlock} (1.11+) and answers inline.
     * 1.8.8's {@code ProjectileHitEvent} exposes ONLY the projectile (javap), and {@code EntityArrow} fires it
     * BEFORE branching on the hit entity and before moving the arrow to the impact — so neither the
     * discrimination nor the landing point is readable during the event. Both become readable one tick later:
     * the block branch sets {@code inGround} and ends the tick at the impact, while an entity hit has already
     * {@code die()}d the arrow. The legacy leaf therefore probes rather than declining, and PROJECTILE_LAND is
     * live on 1.8 for arrows. It stays inert there for throwables and fireballs, whose hit event fires only once
     * the projectile is already {@code dead} — nothing survives to probe.
     */
    void landing(ProjectileHitEvent event, java.util.function.Consumer<Location> land);
}
