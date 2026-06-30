package feature.heroic;

import org.bukkit.Material;

/**
 * Version-agnostic diamond-equivalence maths for the §F heroic forge (docs/v3-directives.md §F). A heroic
 * piece is display-swapped to a weaker material (e.g. gold) but must still FUNCTION as diamond, so rather
 * than rely on per-version Bukkit item-attribute APIs we fold the diamond delta into the plugin's own damage
 * calculation (the additive flat-damage / flat-reduction stage, ADR-0012 §6.1) and emulate diamond max
 * durability by scaling the heroic wear-cancel chance. All pure constants — unit-tested, identical on every
 * version (Paper floor → ceiling and the 1.8 fork), no NMS, no reflection.
 */
public final class HeroicDiamond {

    private HeroicDiamond() {
    }

    /**
     * The flat OUTGOING damage a heroic WEAPON adds so its display material hits like diamond: the diamond
     * attack-damage minus the display material's, never negative (a netherite display is already ≥ diamond).
     * {@code 0} for a non-weapon.
     */
    public static double weaponFlatDamage(Material material) {
        String name = material.name();
        if (name.endsWith("_SWORD")) {
            return Math.max(0.0, 7.0 - attackOf(name, 7.0, 4.0)); // diamond sword 7 vs gold 4
        }
        if (name.endsWith("_AXE")) {
            return Math.max(0.0, 9.0 - attackOf(name, 9.0, 7.0)); // diamond axe 9 vs gold 7
        }
        return 0.0;
    }

    /**
     * The flat INCOMING reduction a heroic ARMOUR piece adds so its display material resists like diamond:
     * the diamond armour benefit of the slot minus the display material's, never negative. A coarse flat
     * stand-in for the vanilla armour-point formula (the user-accepted plugin-calculated approximation).
     * {@code 0} for a non-armour piece.
     */
    public static double armourFlatReduction(Material material) {
        String name = material.name();
        double diamond;
        double display;
        if (name.endsWith("_HELMET")) {
            diamond = 1.5;
            display = armourOf(name, 1.5, 1.0);
        } else if (name.endsWith("_CHESTPLATE")) {
            diamond = 4.0;
            display = armourOf(name, 4.0, 2.5);
        } else if (name.endsWith("_LEGGINGS")) {
            diamond = 3.0;
            display = armourOf(name, 3.0, 1.5);
        } else if (name.endsWith("_BOOTS")) {
            diamond = 1.5;
            display = armourOf(name, 1.5, 0.5);
        } else {
            return 0.0;
        }
        return Math.max(0.0, diamond - display);
    }

    /** Diamond's max durability for a gear kind, or {@code 0} if the material is not heroic-eligible gear. */
    public static int diamondDurability(Material material) {
        String name = material.name();
        if (name.endsWith("_SWORD") || name.endsWith("_AXE")) {
            return 1561;
        }
        if (name.endsWith("_HELMET")) {
            return 363;
        }
        if (name.endsWith("_CHESTPLATE")) {
            return 528;
        }
        if (name.endsWith("_LEGGINGS")) {
            return 495;
        }
        if (name.endsWith("_BOOTS")) {
            return 429;
        }
        return 0;
    }

    /**
     * The effective wear-cancel chance that makes a heroic piece last like diamond. A piece whose REAL max
     * durability is below diamond's (e.g. a gold display piece, max 32) cancels proportionally more wear so it
     * survives ~{@code diamondMax} hits; a piece already at/above diamond keeps the base chance. The real
     * durability bar is the ledger — it depletes at the diamond rate. Always within {@code [baseChance, 1]}.
     */
    public static double scaledWearCancel(int realMax, Material material, double baseChance) {
        int diamondMax = diamondDurability(material);
        if (realMax <= 0 || diamondMax <= 0 || realMax >= diamondMax) {
            return baseChance; // already diamond-durable (or not gear) → just the base 80%-wear chance
        }
        double scaled = 1.0 - ((double) realMax / diamondMax) * (1.0 - baseChance);
        return Math.max(baseChance, Math.min(1.0, scaled));
    }

    /** The display material's own base attack — diamond/netherite display ≥ diamond (no bonus), else the weak value. */
    private static double attackOf(String name, double strongValue, double weakValue) {
        return name.startsWith("DIAMOND_") || name.startsWith("NETHERITE_") ? strongValue : weakValue;
    }

    /** The display material's own armour benefit — diamond/netherite display ≥ diamond (no bonus), else weak. */
    private static double armourOf(String name, double strongValue, double weakValue) {
        return name.startsWith("DIAMOND_") || name.startsWith("NETHERITE_") ? strongValue : weakValue;
    }
}
