package engine.run;

import java.util.Objects;
import java.util.function.IntFunction;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Legacy (1.8.9) impl of {@link ActorProbe} — the era-exclusive {@code overlay/legacy} entity/material reads
 * (ADR-0044; §3.3). 1.8 has no swimming (1.13), no gliding/elytra (1.9), no {@code Material.isAir()} (1.13),
 * and no {@code getItemInMainHand()}; the single held item is {@code getItemInHand()}. These resolve to the
 * 1.8-correct constants.
 */
public final class LegacyActorProbe implements ActorProbe {

    /** Interned potion handle &rarr; live 1.8 type; {@code null} on a miss. */
    private final IntFunction<PotionEffectType> potionHandles;

    /** No handle lookup — {@code %scope.potion.<effect>%} reads 0 (the pre-potion-family form, and tests). */
    public LegacyActorProbe() {
        this(id -> null);
    }

    public LegacyActorProbe(IntFunction<PotionEffectType> potionHandles) {
        this.potionHandles = Objects.requireNonNull(potionHandles, "potionHandles");
    }

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

    @Override
    public int potionLevel(LivingEntity entity, int potionEffectId) {
        PotionEffectType type = potionHandles.apply(potionEffectId);
        if (type == null) {
            return 0;
        }
        // 1.8 has no getPotionEffect(type) — only the bulk list — so this sweeps. It runs lazily, under the
        // condition node, so an ability that never asks about potions never walks anything.
        for (PotionEffect active : entity.getActivePotionEffects()) {
            if (active.getType().equals(type)) {
                return active.getAmplifier() + 1;
            }
        }
        return 0;
    }
}
