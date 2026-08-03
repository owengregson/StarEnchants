package engine.run;

import java.util.Objects;
import java.util.function.IntFunction;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Modern (1.9+/1.13+) impl of {@link ActorProbe} — the era-exclusive {@code overlay/modern} entity/material
 * reads (ADR-0044; §3.3): real swimming/gliding player state, {@code Material.isAir()}, the main-hand read
 * via {@code getItemInMainHand()}, Paper's spawner provenance, and the per-type potion getter.
 */
public final class ModernActorProbe implements ActorProbe {

    /** Interned potion handle &rarr; live type ({@code RuntimeHandles}); {@code null} on a miss. */
    private final IntFunction<PotionEffectType> potionHandles;

    /** No handle lookup — {@code %scope.potion.<effect>%} reads 0 (the pre-potion-family form, and tests). */
    public ModernActorProbe() {
        this(id -> null);
    }

    public ModernActorProbe(IntFunction<PotionEffectType> potionHandles) {
        this.potionHandles = Objects.requireNonNull(potionHandles, "potionHandles");
    }

    @Override
    public boolean isSwimming(Player player) {
        return player.isSwimming();
    }

    @Override
    public boolean isGliding(Player player) {
        return player.isGliding();
    }

    @Override
    public boolean isAir(Material material) {
        return material.isAir();
    }

    @Override
    public String mainHandTypeName(LivingEntity entity) {
        return entity.getEquipment() == null ? null
                : entity.getEquipment().getItemInMainHand().getType().name();
    }

    @Override
    public boolean fromSpawner(LivingEntity entity) {
        return entity.fromMobSpawner(); // Paper carries the flag from 1.16 on, so the 1.17.1 floor has it
    }

    @Override
    public int potionLevel(LivingEntity entity, int potionEffectId) {
        PotionEffectType type = potionHandles.apply(potionEffectId);
        if (type == null) {
            return 0;
        }
        PotionEffect active = entity.getPotionEffect(type);
        return active == null ? 0 : active.getAmplifier() + 1;
    }
}
