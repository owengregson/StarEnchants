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
        ItemStack stack = ItemFactory.build(
                cfg.material(), Material.NETHERITE_SCRAP, cfg.name(), cfg.lore());
        upgrades.mark(stack);
        return stack;
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
        int chance = cfg.successMin() + random.nextInt(cfg.successMax() - cfg.successMin() + 1);
        consume(upgrade); // spent whether the roll succeeds or fails
        if (random.nextInt(100) >= chance) {
            return HeroicResult.committed(gear, messages.format("heroic.fail")); // gear untouched, upgrade spent
        }
        ItemStack upgraded = withUpgradedMaterial(gear, cfg);
        CombatState current = combat.read(upgraded);
        CombatState next = new CombatState(current.enchants(), current.crystals(), current.setKey(),
                current.omni(), new HeroicStat(cfg.percentDamage(), cfg.percentReduction(), cfg.durability()),
                current.added());
        combat.write(upgraded, next);
        lore.apply(upgraded, next); // re-render from state (enchants/crystals + HEROIC marker)
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
