package feature.heroic;

import compile.load.HeroicConfig;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.HeroicStat;
import item.codec.HeroicUpgradeCodec;
import item.mint.ItemFactory;
import item.render.LoreRenderer;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * The heroic upgrade economy (docs/v3-directives.md §F; ADR-0021) — the cold path that MINTS the heroic
 * upgrade item and APPLIES it to a piece of gear with a small randomised success chance. Heroic is NOT
 * set-bound: any armour or weapon may be upgraded. On success it stamps the configured heroic percents
 * onto the piece's {@link CombatState} (the "heroic piece" lore marker then renders from state), and
 * optionally swaps the piece's material to a configured upgrade (within-category, e.g. diamond→netherite)
 * preserving its meta/enchants/PDC; on failure it consumes the upgrade and never harms the gear.
 *
 * <p>Folia-correct: a gesture fires on the clicking player's own region thread, so mutating their
 * cursor/inventory is in-thread. The success roll is the only non-determinism, injected for testability.
 */
public final class HeroicService {

    private final HeroicUpgradeCodec upgrades;
    private final CombatCodec combat;
    private final LoreRenderer lore;
    private final Supplier<HeroicConfig> config;
    private final Random random;
    private final item.lang.Messages messages; // §L lang.yml — heroic apply result + guards

    /** Default-messages form (tests/fixtures). */
    public HeroicService(HeroicUpgradeCodec upgrades, CombatCodec combat, LoreRenderer lore,
                         Supplier<HeroicConfig> config, Random random) {
        this(upgrades, combat, lore, config, random, item.lang.Messages.defaults());
    }

    public HeroicService(HeroicUpgradeCodec upgrades, CombatCodec combat, LoreRenderer lore,
                         Supplier<HeroicConfig> config, Random random, item.lang.Messages messages) {
        this.upgrades = Objects.requireNonNull(upgrades, "upgrades");
        this.combat = Objects.requireNonNull(combat, "combat");
        this.lore = Objects.requireNonNull(lore, "lore");
        this.config = Objects.requireNonNull(config, "config");
        this.random = Objects.requireNonNull(random, "random");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** Whether {@code stack} is a heroic upgrade item. */
    public boolean isUpgrade(ItemStack stack) {
        return upgrades.isUpgrade(stack);
    }

    /** Mint a heroic upgrade item from the configured likeness. */
    public ItemStack mint() {
        HeroicConfig cfg = config.get();
        ItemStack stack = ItemFactory.build(
                ItemFactory.material(cfg.material(), Material.NETHERITE_SCRAP), cfg.name(), cfg.lore());
        upgrades.mark(stack);
        return stack;
    }

    /**
     * Attempt to upgrade {@code gear} with the heroic {@code upgrade} (consumed either way). A roll in the
     * configured {@code [successMin, successMax]} range decides; on success the heroic percents are stamped
     * and the material optionally swapped, on failure the gear is untouched.
     */
    public HeroicResult applyTo(ItemStack upgrade, ItemStack gear) {
        if (gear == null || gear.getType() == Material.AIR) {
            return HeroicResult.unchanged(messages.format("heroic.not-gear"));
        }
        if (gear.getAmount() > 1) {
            return HeroicResult.unchanged(messages.format("common.single-item"));
        }
        if (!combat.read(gear).heroic().isZero()) {
            return HeroicResult.unchanged(messages.format("heroic.already-heroic"));
        }
        HeroicConfig cfg = config.get();
        int chance = cfg.successMin() + random.nextInt(cfg.successMax() - cfg.successMin() + 1);
        consume(upgrade); // a heroic upgrade is spent whether the roll succeeds or fails
        if (random.nextInt(100) >= chance) {
            return HeroicResult.committed(gear, messages.format("heroic.fail")); // gear untouched, upgrade spent
        }
        ItemStack upgraded = withUpgradedMaterial(gear, cfg);
        CombatState current = combat.read(upgraded);
        CombatState next = new CombatState(current.enchants(), current.crystals(), current.setKey(),
                current.omni(), new HeroicStat(cfg.percentDamage(), cfg.percentReduction(), cfg.durability()),
                current.added());
        combat.write(upgraded, next);
        lore.apply(upgraded, next); // re-render: enchants/crystals + the HEROIC marker, from state
        return HeroicResult.committed(upgraded, messages.format("heroic.success"));
    }

    /**
     * The gear with its material swapped to the configured upgrade (preserving meta/enchants/PDC), or the
     * original stack when this material is not remapped or the swap is not viable. The meta copy works for
     * within-category upgrades (the only ones the config should map); any failure falls back to the original.
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
            if (upgraded.setItemMeta(gear.getItemMeta())) { // copies name/lore/enchants/PDC
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
