package feature.compat;

import org.bukkit.entity.Entity;

/**
 * The version edge for projectile-type predicates in combat trigger routing (ADR-0044;
 * docs/legacy-1.8.9-codeshare-design.md §4): {@code Trident} (1.13) and {@code AbstractArrow} (1.14) do not
 * exist on 1.8.9, where the only arrow type is {@code Arrow}. The {@code instanceof} cannot be expressed shared
 * without string compares on a hit path, so two era-exclusive impls back it — {@code ModernProjectiles}
 * ({@code overlay/modern}) and {@code LegacyProjectiles} ({@code overlay/legacy}) — injected into the combat router.
 */
public interface Projectiles {

    boolean isTrident(Entity entity);

    /** Any arrow-family projectile (tipped/spectral/normal) — the BOW-trigger family. */
    boolean isArrowLike(Entity entity);
}
