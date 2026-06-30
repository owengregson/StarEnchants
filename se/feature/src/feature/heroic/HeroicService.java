package feature.heroic;

import compile.load.HeroicConfig;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.HeroicStat;
import item.codec.HeroicUpgradeCodec;
import item.mint.ItemFactory;
import item.render.LoreRenderer;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import platform.item.ItemGroups;

/**
 * Heroic upgrade economy (docs/v3-directives.md §F; ADR-0021) — mints the upgrade and applies it on a
 * success roll. NOT set-bound: any armour or weapon may be upgraded. On success it stamps the heroic
 * percents onto {@link CombatState} and optionally swaps to a configured within-category material
 * (e.g. diamond→netherite) preserving meta/enchants/PDC. The roll is injected for tests.
 */
public final class HeroicService {

    private final HeroicUpgradeCodec upgrades;
    private final CombatCodec combat;
    private final LoreRenderer lore;
    private final Supplier<HeroicConfig> config;
    private final Random random;
    private final item.lang.Messages messages; // §L lang.yml
    private final ItemGroups groups; // §F: gate applyTo to armour/weapons

    /** Default-messages form (tests/fixtures). */
    public HeroicService(HeroicUpgradeCodec upgrades, CombatCodec combat, LoreRenderer lore,
                         Supplier<HeroicConfig> config, Random random) {
        this(upgrades, combat, lore, config, random, item.lang.Messages.defaults());
    }

    /** As above with messages; the armour/weapon group table defaults to {@link ItemGroups#standard()}. */
    public HeroicService(HeroicUpgradeCodec upgrades, CombatCodec combat, LoreRenderer lore,
                         Supplier<HeroicConfig> config, Random random, item.lang.Messages messages) {
        this(upgrades, combat, lore, config, random, messages, ItemGroups.standard());
    }

    public HeroicService(HeroicUpgradeCodec upgrades, CombatCodec combat, LoreRenderer lore,
                         Supplier<HeroicConfig> config, Random random, item.lang.Messages messages,
                         ItemGroups groups) {
        this.upgrades = Objects.requireNonNull(upgrades, "upgrades");
        this.combat = Objects.requireNonNull(combat, "combat");
        this.lore = Objects.requireNonNull(lore, "lore");
        this.config = Objects.requireNonNull(config, "config");
        this.random = Objects.requireNonNull(random, "random");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.groups = Objects.requireNonNull(groups, "groups");
    }

    public boolean isUpgrade(ItemStack stack) {
        return upgrades.isUpgrade(stack);
    }

    public ItemStack mint() {
        HeroicConfig cfg = config.get();
        // GOLD_INGOT is on every version; the configured material resolves by name first. Name + lore carry
        // {PERCENT}/{+/-}/{AMOUNT}/{KINDS} placeholders filled from the live config so the ingot self-documents.
        List<String> lore = new java.util.ArrayList<>(cfg.lore().size());
        for (String line : cfg.lore()) {
            lore.add(fillPlaceholders(line, cfg));
        }
        ItemStack stack = ItemFactory.buildItem(
                cfg.material(), Material.GOLD_INGOT, fillPlaceholders(cfg.name(), cfg), lore);
        upgrades.mark(stack);
        return stack;
    }

    /**
     * Fill the heroic item's {@code {PERCENT}}/{@code {1-PERCENT}}/{@code {KINDS}} placeholders, plus a
     * per-stat {@code {+/-}{AMOUNT}} chosen by the line it sits on (Outgoing → +damage, Incoming → -reduction,
     * Durability → +the wear-saving), so the three stat lines can share one template token.
     */
    private String fillPlaceholders(String line, HeroicConfig cfg) {
        int success = cfg.successMax(); // headline success rate (cosmic sets min == max for a single number)
        String out = line;
        if (out.contains("Outgoing")) {
            out = signed(out, "+", pct(cfg.percentDamage()));
        } else if (out.contains("Incoming")) {
            out = signed(out, "-", pct(cfg.percentReduction()));
        } else if (out.contains("Durability")) {
            out = signed(out, "+", pct(cfg.durability()));
        }
        return out
                .replace("{1-PERCENT}", Integer.toString(100 - success))
                .replace("{PERCENT}", Integer.toString(success))
                .replace("{KINDS}", ItemGroups.kindsLabel(List.of("ARMOR", "WEAPON")));
    }

    private static String signed(String line, String sign, int amount) {
        return line.replace("{+/-}", sign).replace("{AMOUNT}", Integer.toString(amount));
    }

    private static int pct(double fraction) {
        return (int) Math.round(fraction * 100.0);
    }

    /** Attempt to upgrade {@code gear} with the heroic {@code upgrade} (consumed either way). */
    public HeroicResult applyTo(ItemStack upgrade, ItemStack gear) {
        if (gear == null || gear.getType() == Material.AIR) {
            return HeroicResult.unchanged(messages.format("heroic.not-gear"));
        }
        if (gear.getAmount() > 1) {
            return HeroicResult.unchanged(messages.format("common.single-item"));
        }
        if (!groups.matches(gear.getType(), List.of("ARMOR", "WEAPON"))) {
            return HeroicResult.unchanged(messages.format("heroic.not-gear")); // §F: armour/weapons only
        }
        if (!combat.read(gear).heroic().isZero()) {
            return HeroicResult.unchanged(messages.format("heroic.already-heroic"));
        }
        HeroicConfig cfg = config.get();
        boolean weapon = groups.matches(gear.getType(), List.of("WEAPON")); // else armour (validated above)
        int chance = cfg.successMin() + random.nextInt(cfg.successMax() - cfg.successMin() + 1);
        consume(upgrade); // spent whether the roll succeeds or fails
        if (random.nextInt(100) >= chance) {
            // Like the other consumables: a failed attempt optionally DESTROYS the gear (else leaves it intact).
            ItemStack result = cfg.destroyOnFail() ? null : gear;
            return HeroicResult.committed(result, messages.format("heroic.fail"));
        }
        ItemStack upgraded = withUpgradedMaterial(gear, cfg);
        // §F diamond-equivalence (gold display, diamond function): when diamond-stats is on, the display-swapped
        // piece carries the diamond base-stat delta as a flat damage/reduction folded into the plugin's own
        // combat maths (HeroicDiamond) — version-uniform, no item-attribute API. Durability is emulated by the
        // wear-cancel scaling at hit time. The native material's stats already ≥ diamond → delta 0 (no-op).
        double flatDamage = cfg.diamondStats() && weapon ? HeroicDiamond.weaponFlatDamage(upgraded.getType()) : 0.0;
        double flatReduction = cfg.diamondStats() && !weapon
                ? HeroicDiamond.armourFlatReduction(upgraded.getType()) : 0.0;
        // Stat separation (§F): a WEAPON carries the OUTGOING bonus, ARMOUR the INCOMING reduction — never both,
        // so a heroic sword can't inflate defence nor heroic armour inflate attack.
        HeroicStat stat = weapon
                ? new HeroicStat(cfg.percentDamage(), 0.0, cfg.durability(), flatDamage, 0.0)
                : new HeroicStat(0.0, cfg.percentReduction(), cfg.durability(), 0.0, flatReduction);
        CombatState current = combat.read(upgraded);
        CombatState next = current.withHeroic(stat); // preserves setWeaponKey (a heroic-upgraded set weapon stays one)
        combat.write(upgraded, next);
        lore.apply(upgraded, next); // re-render from state (enchants/crystals + HEROIC line)
        return HeroicResult.committed(upgraded, messages.format("heroic.success"));
    }

    /**
     * Gear with its material swapped to the configured upgrade (meta/enchants/PDC preserved), or the original
     * when not remapped or the swap is unviable. The meta copy only works within-category.
     */
    private ItemStack withUpgradedMaterial(ItemStack gear, HeroicConfig cfg) {
        String token = cfg.upgradeFor(gear.getType().name());
        if (token == null) {
            return gear;
        }
        Material target = ItemFactory.material(token, gear.getType());
        if (target == gear.getType()) {
            return gear;
        }
        try {
            ItemStack upgraded = new ItemStack(target, gear.getAmount());
            if (upgraded.setItemMeta(gear.getItemMeta())) {
                return upgraded;
            }
        } catch (RuntimeException incompatible) {
            // cross-category meta copy rejected — keep the original material, still apply heroic stats
        }
        return gear;
    }

    private static void consume(ItemStack stack) {
        stack.setAmount(stack.getAmount() - 1);
    }
}
