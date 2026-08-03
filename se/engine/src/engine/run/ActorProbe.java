package engine.run;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * The version edge for the entity/material predicates {@link FactPopulator} reads into condition facts
 * (docs/legacy-1.8.9-codeshare-design.md §3.3): the swimming (1.13) and gliding/elytra (1.9) player states,
 * {@code Material.isAir()} (1.13), and the main-hand read (off-hand vs 1.8 {@code getItemInHand()}) all shifted
 * across the range and are absent on 1.8.9. Two era-exclusive impls back it — {@code ModernActorProbe}
 * (overlay/modern) and {@code LegacyActorProbe} (overlay/legacy, 1.8-correct constants) — injected into
 * FactPopulator, threaded from the composition root alongside the era-built {@code SinkFactory} (ADR-0044).
 */
public interface ActorProbe {

    /** Whether {@code player} is swimming (always false on 1.8 — no swimming mechanic). */
    boolean isSwimming(Player player);

    /** Whether {@code player} is gliding (always false on 1.8 — no elytra/gliding). */
    boolean isGliding(Player player);

    /** Whether {@code material} is air ({@code == Material.AIR} on 1.8 — no {@code Material.isAir()}). */
    boolean isAir(Material material);

    /** The entity's main-hand item type name, or {@code null} if it has no equipment (1.8: empty hand → {@code "AIR"}). */
    String mainHandTypeName(LivingEntity entity);

    /**
     * Whether {@code entity} came out of a mob spawner ({@code %victim.fromspawner%}) — the grinder test that keeps
     * a spawner farm from paying loot/soul bonuses. Always false on 1.8, which tracks no spawn provenance at all.
     */
    boolean fromSpawner(LivingEntity entity);

    /**
     * {@code entity}'s active level of the potion effect interned as {@code potionEffectId} — {@code amplifier + 1},
     * or {@code 0} when absent ({@code %actor.potion.<effect>%} / {@code %victim.potion.<effect>%}). The id is the
     * handle the compiler already resolved (§9), so the era impl only turns it back into a live type. Modern asks
     * the entity directly; 1.8 has no per-type getter and sweeps the active list.
     */
    int potionLevel(LivingEntity entity, int potionEffectId);
}
