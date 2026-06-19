package tester.suite;

import compile.load.HeroicConfig;
import feature.heroic.HeroicResult;
import feature.heroic.HeroicService;
import item.codec.CombatCodec;
import item.codec.HeroicUpgradeCodec;
import item.codec.ItemKeys;
import item.render.LoreRenderer;
import item.render.LoreStyle;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import platform.item.ItemGroups;
import tester.harness.Harness;

/**
 * The heroic APPLY path, proven live (docs/v3-directives.md §F): {@link HeroicService#applyTo} stamps the
 * heroic percents on a successful roll (swapping the material when configured), consumes the upgrade on
 * success OR a failed roll, and REJECTS (without consuming) an already-heroic piece or a non-armour/weapon.
 * Item-only (no fake player), so it runs across the WHOLE range. Determinism comes from 100%/0% success
 * configs rather than seed reverse-engineering.
 *
 * <ul>
 *   <li>{@code heroic.apply.success} — a successful roll stamps HeroicStat, swaps DIAMOND→NETHERITE, consumes the upgrade.</li>
 *   <li>{@code heroic.apply.fail} — a failed roll leaves the gear untouched but consumes the upgrade.</li>
 *   <li>{@code heroic.apply.alreadyHeroic} — re-applying to a heroic piece is rejected and does NOT consume.</li>
 *   <li>{@code heroic.apply.notGear} — applying to a non-armour/weapon (STICK) is rejected and does NOT consume.</li>
 * </ul>
 */
public final class HeroicApplySuite implements Harness.Scenario {

    private final Plugin plugin;

    public HeroicApplySuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("heroic.apply.success");
        h.expect("heroic.apply.fail");
        h.expect("heroic.apply.alreadyHeroic");
        h.expect("heroic.apply.notGear");

        HeroicUpgradeCodec upgrades = new HeroicUpgradeCodec(ItemKeys.of(plugin).heroicUpgrade());
        CombatCodec combat = new CombatCodec(ItemKeys.of(plugin).combat());
        LoreRenderer lore = new LoreRenderer(LoreStyle.DEFAULT, key -> null);
        HeroicService always = service(upgrades, combat, lore, 100); // roll always succeeds
        HeroicService never = service(upgrades, combat, lore, 0);    // roll always fails

        h.guard("heroic.apply.success", () -> {
            ItemStack upgrade = always.mint();
            ItemStack gear = new ItemStack(Material.DIAMOND_SWORD);
            HeroicResult result = always.applyTo(upgrade, gear);
            if (!result.commit()) {
                throw new IllegalStateException("a 100% roll should succeed");
            }
            ItemStack out = result.newTarget();
            if (out.getType() != Material.NETHERITE_SWORD) {
                throw new IllegalStateException("the material was not upgraded to netherite: " + out.getType());
            }
            if (combat.read(out).heroic().isZero()) {
                throw new IllegalStateException("HeroicStat was not stamped on success");
            }
            if (upgrade.getAmount() != 0) {
                throw new IllegalStateException("the upgrade was not consumed on success");
            }
        });

        h.guard("heroic.apply.fail", () -> {
            ItemStack upgrade = never.mint();
            ItemStack gear = new ItemStack(Material.DIAMOND_SWORD);
            HeroicResult result = never.applyTo(upgrade, gear);
            if (!result.commit()) {
                throw new IllegalStateException("a failed roll still commits (the upgrade is spent)");
            }
            if (!result.newTarget().getType().equals(Material.DIAMOND_SWORD)
                    || !combat.read(result.newTarget()).heroic().isZero()) {
                throw new IllegalStateException("the gear must be untouched on a failed roll");
            }
            if (upgrade.getAmount() != 0) {
                throw new IllegalStateException("the upgrade is consumed even on a failed roll");
            }
        });

        h.guard("heroic.apply.alreadyHeroic", () -> {
            // Make a piece heroic, then re-apply to the returned heroic stack — the second apply must reject.
            ItemStack heroicGear = always.applyTo(always.mint(), new ItemStack(Material.DIAMOND_CHESTPLATE)).newTarget();
            if (combat.read(heroicGear).heroic().isZero()) {
                throw new IllegalStateException("setup: the gear should be heroic after the first apply");
            }
            ItemStack upgrade = always.mint();
            HeroicResult result = always.applyTo(upgrade, heroicGear);
            if (result.commit()) {
                throw new IllegalStateException("an already-heroic piece must be rejected");
            }
            if (upgrade.getAmount() != 1) {
                throw new IllegalStateException("a guard rejection must NOT consume the upgrade");
            }
        });

        h.guard("heroic.apply.notGear", () -> {
            ItemStack upgrade = always.mint();
            ItemStack notGear = new ItemStack(Material.STICK);
            HeroicResult result = always.applyTo(upgrade, notGear);
            if (result.commit()) {
                throw new IllegalStateException("a non-armour/weapon must be rejected");
            }
            if (upgrade.getAmount() != 1) {
                throw new IllegalStateException("a not-gear rejection must NOT consume the upgrade");
            }
        });
    }

    /** A heroic upgrade item that is already heroic (for the already-heroic guard). */
    private static ItemStack reHeroic(HeroicService always, CombatCodec combat) {
        ItemStack g = new ItemStack(Material.NETHERITE_BOOTS);
        ItemStack out = always.applyTo(always.mint(), g).newTarget();
        return out; // its CombatState carries a non-zero HeroicStat
    }

    private HeroicService service(HeroicUpgradeCodec upgrades, CombatCodec combat, LoreRenderer lore, int chance) {
        HeroicConfig cfg = new HeroicConfig("NETHERITE_SCRAP", "&6Heroic", List.of(), chance, chance,
                0.10, 0.10, 0.25,
                Map.of("DIAMOND_SWORD", "NETHERITE_SWORD", "DIAMOND_CHESTPLATE", "NETHERITE_CHESTPLATE"),
                "ENTITY");
        return new HeroicService(upgrades, combat, lore, () -> cfg, new Random(), item.lang.Messages.defaults(),
                ItemGroups.standard());
    }
}
