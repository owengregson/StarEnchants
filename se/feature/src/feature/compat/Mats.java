package feature.compat;

import org.bukkit.Material;

/**
 * Version-safe Material resolution by name, replacing compile-time enum literals that do not exist on the
 * 1.8.9 floor (e.g. {@code NETHERITE_SCRAP}, {@code CHORUS_FRUIT}, the flattened {@code INK_SAC}/dye names).
 * {@code Material.getMaterial(String)} resolves on every version, so this is shared (not an overlay): on
 * modern it returns the same constant as the literal; on 1.8 a modern-only name resolves to {@code null}
 * (the caller supplies a 1.8 fallback via {@link #or}). Mapping a modern name to its 1.8 equivalent is the
 * Gate-3 legacy Material table (docs/legacy-1.8.9-codeshare-design.md §6 R3, Phase 3).
 */
public final class Mats {

    private Mats() {
    }

    /** The Material named {@code name}, or {@code null} if it does not exist on this version. */
    public static Material of(String name) {
        return Material.getMaterial(name);
    }

    /** The Material named {@code name}, or {@code fallback} (a floor-stable Material) when absent on this version. */
    public static Material or(String name, Material fallback) {
        Material material = Material.getMaterial(name);
        return material != null ? material : fallback;
    }

    /** Whether {@code material} is empty (no item). 1.8 has no {@code Material.isAir()}; {@code == AIR} is the cross-version test for an item slot. */
    public static boolean isAir(Material material) {
        return material == Material.AIR;
    }
}
