package tester.suite;

import com.google.common.collect.Multimap;
import compile.load.HeroicConfig;
import feature.heroic.HeroicDiamond;
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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import platform.item.ItemGroups;
import tester.harness.Harness;

/**
 * Heroic vanilla-stats, live (§F; ADR-0031): forging a sub-diamond ARMOUR display piece with {@code vanilla-stats}
 * on must stamp REAL vanilla armour-point + toughness attribute modifiers (diamond values, on the piece's slot)
 * so the armour is correct on the HUD and for a plugin that recomputes from vanilla armour (e.g. Mental's 1.8
 * restore) — the whole point of the feature. Where the platform supports a custom max durability (1.20.5+), the
 * piece must also carry a diamond max. Asserts the REAL server ItemMeta (the cross-version-sensitive surface: the
 * registry-key attribute resolve, the modifier ctor, addAttributeModifier, and setMaxDamage round-trip) on every
 * matrix version. Item-only; deterministic via a 100% success config. The 1.8.9 fork no-ops this (legacy smoke).
 */
public final class HeroicVanillaStatsSuite implements Harness.Scenario {

    private final Plugin plugin;

    public HeroicVanillaStatsSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("heroic.vanilla.armour");
        h.expect("heroic.vanilla.hiddenTooltip");
        h.expect("heroic.vanilla.durability");

        HeroicUpgradeCodec upgrades = new HeroicUpgradeCodec(ItemKeys.of().heroicUpgrade());
        CombatCodec combat = new CombatCodec(ItemKeys.of().combat());
        LoreRenderer lore = new LoreRenderer(LoreStyle.DEFAULT, key -> null);
        HeroicService service = service(upgrades, combat, lore);

        // Forge a diamond chestplate → gold display once; assert its real meta across the checks below.
        ItemStack forged = forgeGoldChestplate(service);
        ItemMeta meta = forged.getItemMeta();

        h.guard("heroic.vanilla.armour", () -> {
            if (forged.getType() != Material.GOLDEN_CHESTPLATE) {
                throw new IllegalStateException("the heroic display should be a gold chestplate, was " + forged.getType());
            }
            Multimap<Attribute, AttributeModifier> chest = meta.getAttributeModifiers(EquipmentSlot.CHEST);
            if (chest == null || chest.isEmpty()) {
                throw new IllegalStateException("no CHEST attribute modifiers were written — armour not re-statted");
            }
            // Real diamond chestplate values: 8 armour + 2 toughness. Match by amount (version-agnostic; avoids
            // referencing a renamed Attribute constant) — a sub-diamond gold piece has no other CHEST modifiers.
            double armour = HeroicDiamond.diamondArmourPoints(Material.GOLDEN_CHESTPLATE);   // 8
            double toughness = HeroicDiamond.diamondArmourToughness(Material.GOLDEN_CHESTPLATE); // 2
            boolean hasArmour = false;
            boolean hasToughness = false;
            for (AttributeModifier mod : chest.values()) {
                if (mod.getAmount() == armour) {
                    hasArmour = true;
                }
                if (mod.getAmount() == toughness) {
                    hasToughness = true;
                }
            }
            if (!hasArmour || !hasToughness) {
                throw new IllegalStateException("CHEST modifiers must include diamond armour " + armour
                        + " and toughness " + toughness + ", got " + chest.values());
            }
        });

        h.guard("heroic.vanilla.hiddenTooltip", () -> {
            // The HEROIC lore is canonical; the attribute lines are hidden so the modifiers drive the HUD silently.
            if (!meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES)) {
                throw new IllegalStateException("HIDE_ATTRIBUTES must be set so the vanilla attribute lines stay hidden");
            }
        });

        h.guard("heroic.vanilla.durability", () -> {
            int diamondMax = HeroicDiamond.diamondDurability(Material.GOLDEN_CHESTPLATE); // 528
            int goldMax = Material.GOLDEN_CHESTPLATE.getMaxDurability();
            int custom = customMaxDurability(meta); // -1 when the platform has no custom max (pre-1.20.5)
            int actualMax = custom >= 0 ? custom : goldMax;
            int expected = supportsMaxDamageOverride() ? diamondMax : goldMax;
            if (actualMax != expected) {
                throw new IllegalStateException("max durability should be " + expected + ", was " + actualMax);
            }
        });
    }

    /** A 100%-success heroic service that swaps a diamond chestplate to its gold twin with vanilla-stats ON. */
    private HeroicService service(HeroicUpgradeCodec upgrades, CombatCodec combat, LoreRenderer lore) {
        HeroicConfig cfg = new HeroicConfig("NETHERITE_SCRAP", "&6Heroic", List.of(), 100, 100,
                0.10, 0.10, 0.20,
                Map.of("DIAMOND_CHESTPLATE", "GOLDEN_CHESTPLATE"),
                "ENTITY", "", false, true /* diamond-stats */, true /* vanilla-stats */);
        return new HeroicService(upgrades, combat, lore, () -> cfg, new Random(), item.lang.Messages.defaults(),
                ItemGroups.standard());
    }

    private static ItemStack forgeGoldChestplate(HeroicService service) {
        ItemStack upgrade = service.mint();
        ItemStack gear = new ItemStack(Material.DIAMOND_CHESTPLATE);
        HeroicResult result = service.applyTo(upgrade, gear);
        if (!result.commit() || result.newTarget() == null) {
            throw new IllegalStateException("a 100% heroic roll should succeed and produce a piece");
        }
        return result.newTarget();
    }

    /** Whether this platform exposes a custom max durability ({@code Damageable.setMaxDamage}, 1.20.5+). */
    private static boolean supportsMaxDamageOverride() {
        try {
            Damageable.class.getMethod("setMaxDamage", Integer.class);
            return true;
        } catch (NoSuchMethodException notInteger) {
            try {
                Damageable.class.getMethod("setMaxDamage", int.class);
                return true;
            } catch (NoSuchMethodException absent) {
                return false;
            }
        }
    }

    /** The item's custom max durability (1.20.5+) via reflection, or {@code -1} when it carries none / unsupported. */
    private static int customMaxDurability(ItemMeta meta) {
        if (!(meta instanceof Damageable)) {
            return -1;
        }
        try {
            if ((boolean) Damageable.class.getMethod("hasMaxDamage").invoke(meta)) {
                return ((Number) Damageable.class.getMethod("getMaxDamage").invoke(meta)).intValue();
            }
        } catch (ReflectiveOperationException absent) {
            // pre-1.20.5: no custom-max API
        }
        return -1;
    }
}
