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
     * Where a {@link ProjectileHitEvent}'s projectile came down, or {@code null} when this era cannot say it
     * landed on the world — an entity hit (which BOW/ATTACK already dispatches, so PROJECTILE_LAND must not),
     * or an era whose event carries no hit accessors at all.
     *
     * <p>Era split, both halves verified against the shipped jars: modern reads {@code getHitEntity} /
     * {@code getHitBlock} (1.11+). 1.8.8's {@code ProjectileHitEvent} exposes ONLY the projectile (javap), is
     * fired for entity hits as well as block hits ({@code EntityArrow} calls
     * {@code CraftEventFactory.callProjectileHitEvent} BEFORE branching on the hit entity), and fires while the
     * arrow still sits at its pre-move position — the hit point is only written afterwards. Neither the
     * discrimination nor the landing point is derivable there, so the legacy leaf answers {@code null} and the
     * trigger is inert on 1.8, exactly as ITEM_DAMAGE is inert without {@code PlayerItemDamageEvent} (§4).
     */
    Location landingOf(ProjectileHitEvent event);
}
