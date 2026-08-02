package engine.run;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;

/**
 * Legacy (1.8.9) impl of {@link ActorProbe} — the era-exclusive {@code overlay/legacy} entity/material reads
 * (ADR-0044; §3.3). 1.8 has no swimming (1.13), no gliding/elytra (1.9), no {@code Material.isAir()} (1.13),
 * and no {@code getItemInMainHand()}; the single held item is {@code getItemInHand()}. These resolve to the
 * 1.8-correct constants.
 */
public final class LegacyActorProbe implements ActorProbe {

    @Override
    public boolean isSwimming(Player player) {
        return false; // no swimming mechanic on 1.8
    }

    @Override
    public boolean isGliding(Player player) {
        return false; // no elytra/gliding on 1.8
    }

    @Override
    public boolean isAir(Material material) {
        return material == Material.AIR; // no Material.isAir() on 1.8
    }

    @Override
    @SuppressWarnings("deprecation") // getItemInHand is the 1.8 main-hand accessor (no getItemInMainHand on 1.8)
    public String mainHandTypeName(LivingEntity entity) {
        if (entity.getEquipment() == null) {
            return null;
        }
        // 1.8 getItemInHand() returns null for an empty hand (modern getItemInMainHand() returns AIR); normalize
        // to AIR so the shared FactPopulator sees the SAME "AIR" name on both eras instead of NPE-ing the fact loop.
        ItemStack held = entity.getEquipment().getItemInHand();
        return held == null ? Material.AIR.name() : held.getType().name();
    }

    @Override
    public boolean fromSpawner(LivingEntity entity) {
        return false; // 1.8 records no spawn provenance — the server keeps no flag to read
    }
}
