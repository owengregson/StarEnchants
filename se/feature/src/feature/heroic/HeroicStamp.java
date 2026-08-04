package feature.heroic;

import compile.load.HeroicConfig;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.HeroicStat;
import item.render.LoreRenderer;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.inventory.ItemStack;

/**
 * Writes the pack's heroic stats onto a piece of gear (§F, ADR-0021) — the STATS half of the upgrade gesture,
 * with none of its economy: no success roll, no consumed upgrade item, no material swap. Two callers share it:
 * {@link HeroicService#applyTo} after its roll succeeds, and the set minter for a member declaring
 * {@code heroic: true} (a piece the pack ships already heroic, which is what makes
 * {@code %victim.heroicpieces%} readable without anyone consuming an upgrade first).
 *
 * <p>The material is deliberately NOT upgraded here: an authored set piece already names its final likeness,
 * and swapping it at mint would overwrite the appearance the pack chose.
 */
public final class HeroicStamp implements feature.apply.HeroicMint {

    private final Supplier<HeroicConfig> config;
    private final VanillaStats vanillaStats; // §F real vanilla diamond attrs (§4 era seam; NONE on 1.8)
    private final CombatCodec combat;
    private final LoreRenderer lore;

    public HeroicStamp(Supplier<HeroicConfig> config, VanillaStats vanillaStats, CombatCodec combat,
                       LoreRenderer lore) {
        this.config = Objects.requireNonNull(config, "config");
        this.vanillaStats = Objects.requireNonNull(vanillaStats, "vanillaStats");
        this.combat = Objects.requireNonNull(combat, "combat");
        this.lore = Objects.requireNonNull(lore, "lore");
    }

    /**
     * Make {@code gear} heroic in place and re-render it from the new state. No-op (returning {@code false})
     * on absent gear or on a piece that already carries heroic stats — the same guard the gesture applies, so
     * a re-mint or a double stamp can never sum the tier onto itself.
     *
     * @param weapon {@code true} for the outgoing-damage side, {@code false} for the incoming-reduction side
     */
    @Override
    public boolean stampOn(ItemStack gear, boolean weapon) {
        if (gear == null) {
            return false;
        }
        CombatState current = combat.read(gear);
        if (!current.heroic().isZero()) {
            return false;
        }
        CombatState next = current.withHeroic(statFor(gear, weapon));
        combat.write(gear, next);
        lore.apply(gear, next); // re-render from state (enchants/crystals + the HEROIC line)
        return true;
    }

    /**
     * The stats a heroic piece carries, and the vanilla-attribute write that decides them. §F
     * diamond-equivalence (gold display, diamond function): when {@code diamond-stats} + {@code vanilla-stats}
     * are on, the writer stamps REAL vanilla modifiers and we DROP the matching plugin-maths flat delta so the
     * two never double-count. Off (or on the 1.8 fork, whose writer no-ops): keep the {@link HeroicDiamond}
     * flat fold. Stat separation (§F): a WEAPON carries the OUTGOING bonus, ARMOUR the INCOMING reduction —
     * never both, so a heroic sword cannot inflate defence nor heroic armour inflate attack.
     */
    private HeroicStat statFor(ItemStack gear, boolean weapon) {
        HeroicConfig cfg = config.get();
        boolean realStats = cfg.diamondStats() && cfg.vanillaStats() && vanillaStats.apply(gear, weapon);
        double flatDamage = cfg.diamondStats() && weapon && !realStats
                ? HeroicDiamond.weaponFlatDamage(gear.getType()) : 0.0;
        double flatReduction = cfg.diamondStats() && !weapon && !realStats
                ? HeroicDiamond.armourFlatReduction(gear.getType()) : 0.0;
        return weapon
                ? new HeroicStat(cfg.percentDamage(), 0.0, cfg.durability(), flatDamage, 0.0)
                : new HeroicStat(0.0, cfg.percentReduction(), cfg.durability(), 0.0, flatReduction);
    }
}
