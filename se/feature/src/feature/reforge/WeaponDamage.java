package feature.reforge;

import org.bukkit.entity.Player;

/**
 * One weapon-swing's raw damage for {@code holder}'s current main hand (ADR-0071 Javelin). Era
 * seam (ADR-0044): modern reads the live GENERIC_ATTACK_DAMAGE attribute value — which includes
 * the held weapon's modifier on 1.9+ — through the resolver ALIAS chain (the 1.21.3+ attribute
 * rename trap, masks lesson); 1.8 has no player-side weapon attribute, so the legacy impl maps
 * the held material through the vanilla 1.8 damage table (+1 fist base). Values are RAW
 * health-space — the felt hit is calibrated with /se damagedebug per §1.
 */
public interface WeaponDamage {

    double swingDamage(Player holder);

    /** Inert default for tests/wiring stubs. */
    WeaponDamage NONE = holder -> 1.0;

    /**
     * The vanilla 1.8 melee damage for a material NAME (never a modern {@code Material} constant — the CLOCK
     * trap): swords wood/gold 4, stone 5, iron 6, diamond 7; axes wood/gold 3, stone 4, iron 5, diamond 6;
     * every other hand, and an empty one ({@code null}), is the 1-damage fist. Lives on the shared seam so the
     * legacy impl stays a thin null-guarded lookup and the table is unit-testable without a legacy-tree compile.
     */
    static double legacyMaterialDamage(String materialName) {
        if (materialName == null) {
            return 1.0;
        }
        switch (materialName) {
            case "WOOD_SWORD":
            case "GOLD_SWORD":
                return 4.0;
            case "STONE_SWORD":
                return 5.0;
            case "IRON_SWORD":
                return 6.0;
            case "DIAMOND_SWORD":
                return 7.0;
            case "WOOD_AXE":
            case "GOLD_AXE":
                return 3.0;
            case "STONE_AXE":
                return 4.0;
            case "IRON_AXE":
                return 5.0;
            case "DIAMOND_AXE":
                return 6.0;
            default:
                return 1.0;
        }
    }
}
