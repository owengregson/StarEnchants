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
     * Diamond's total attack damage for a weapon kind (base hand 1.0 included, as the vanilla "N Attack Damage"
     * tooltip shows): {@code sword 7, axe 9}, or {@code 0} for a non-weapon. The REAL value written as a
     * {@code GENERIC_ATTACK_DAMAGE} modifier when vanilla-stats is on (the modifier amount is this minus the
     * base 1.0), so a heroic gold weapon attacks and reads as diamond. Distinct from {@link #weaponFlatDamage}
     * (the plugin-maths fold approximation used when the attribute isn't written).
     */
    public static double diamondAttackDamage(Material material) {
        String name = material.name();
        if (name.endsWith("_SWORD")) {
            return 7.0;
        }
        if (name.endsWith("_AXE")) {
            return 9.0;
        }
        return 0.0;
    }

    /**
     * The {@code DIAMOND_<kind>} material NAME a sub-diamond heroic piece stands in for — the value of the
     * neutral {@code combat:effective_material} marker so an era-combat plugin treats the display-swapped piece
     * as diamond. {@code null} when {@code material} is already diamond/netherite (no stand-in needed) or is not
     * heroic-eligible gear (helmet/chestplate/leggings/boots/sword/axe). A pure string — the codec stores the name.
     */
    public static String diamondMaterialName(Material material) {
        String name = material.name();
        if (name.startsWith("DIAMOND_") || name.startsWith("NETHERITE_")) {
            return null; // already at least diamond — it IS its own true material
        }
        for (String kind : new String[] {"_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS", "_SWORD", "_AXE"}) {
            if (name.endsWith(kind)) {
                return "DIAMOND" + kind;
            }
        }
        return null; // not heroic-eligible gear
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

    /**
     * Diamond's vanilla armour-point benefit for a piece's slot ({@code helmet 3, chestplate 8, leggings 6,
     * boots 3}), or {@code 0} if {@code material} is not a heroic armour piece. Unlike {@link #armourFlatReduction}
     * (a flat-reduction approximation for the plugin's own combat maths), these are the REAL vanilla armour points
     * written as a {@code GENERIC_ARMOR} attribute modifier when {@code vanilla-stats} is on (ADR-0031), so the
     * armour is correct on the HUD and for plugins that recompute from vanilla armour (e.g. Mental's 1.8 restore).
     */
    public static int diamondArmourPoints(Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET")) {
            return 3;
        }
        if (name.endsWith("_CHESTPLATE")) {
            return 8;
        }
        if (name.endsWith("_LEGGINGS")) {
            return 6;
        }
        if (name.endsWith("_BOOTS")) {
            return 3;
        }
        return 0;
    }

    /** Diamond's armour toughness per piece ({@code 2}), or {@code 0} if {@code material} is not an armour piece. */
    public static int diamondArmourToughness(Material material) {
        return diamondArmourPoints(material) > 0 ? 2 : 0;
    }

    /**
     * Whether {@code material} is a SUB-diamond armour piece — an armour piece whose real armour points are below
     * diamond's, so re-stating it to diamond attribute values is an upgrade. A diamond/netherite display is already
     * ≥ diamond (no override). The signal for when the vanilla-stats writer should replace the piece's defaults.
     */
    public static boolean displayBelowDiamondArmour(Material material) {
        String name = material.name();
        return diamondArmourPoints(material) > 0
                && !name.startsWith("DIAMOND_") && !name.startsWith("NETHERITE_");
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
