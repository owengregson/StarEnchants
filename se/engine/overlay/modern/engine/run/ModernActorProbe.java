package engine.run;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Modern (1.9+/1.13+) impl of {@link ActorProbe} — the era-exclusive {@code overlay/modern} entity/material
 * reads (ADR-0044; §3.3): real swimming/gliding player state, {@code Material.isAir()}, and the main-hand read
 * via {@code getItemInMainHand()}.
 */
public final class ModernActorProbe implements ActorProbe {

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
}
