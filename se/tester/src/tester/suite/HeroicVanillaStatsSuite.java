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
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import platform.item.ItemGroups;
import tester.harness.Harness;

/**
 * Heroic vanilla-stats, live (§F; ADR-0031/0032): forging a sub-diamond piece with {@code vanilla-stats} on
 * must stamp REAL vanilla attribute modifiers (diamond values, on the piece's slot) — armour-point+toughness
 * for armour, attack-damage for a weapon — the vendor-neutral {@code combat:effective_material} marker, and
 * (1.20.5+) a diamond max durability, and must NOT set {@code HIDE_ATTRIBUTES} (the explicit modifiers suppress
 * the display material's defaults, so the vanilla tooltip renders the diamond values). Asserts the REAL server
 * ItemMeta (the cross-version-sensitive surface: the registry-key attribute resolve, the modifier ctor,
 * addAttributeModifier, the PDC marker, and setMaxDamage) on every matrix version. Item-only; deterministic via
 * a 100% success config. The 1.8.9 fork no-ops the writer (legacy smoke).
 */
public final class HeroicVanillaStatsSuite implements Harness.Scenario {

    /** The vendor-neutral cross-plugin marker key (ADR-0032). */
    private static final NamespacedKey EFFECTIVE_MATERIAL = NamespacedKey.fromString("combat:effective_material");

    /** Item-only suite — no server/scheduling needed, so no plugin handle. */
    public HeroicVanillaStatsSuite() {
    }

    @Override
    public void accept(Harness h) {
        h.expect("heroic.vanilla.armour");
        h.expect("heroic.vanilla.weapon");
        h.expect("heroic.vanilla.visibleTooltip");
        h.expect("heroic.vanilla.marker");
        h.expect("heroic.vanilla.durability");

        HeroicService service = service();

        // Forge a diamond chestplate → gold and a diamond sword → gold once; assert their real meta below.
        ItemStack chest = forge(service, Material.DIAMOND_CHESTPLATE, Material.GOLDEN_CHESTPLATE);
        ItemStack sword = forge(service, Material.DIAMOND_SWORD, Material.GOLDEN_SWORD);
        ItemMeta chestMeta = chest.getItemMeta();
        ItemMeta swordMeta = sword.getItemMeta();

        h.guard("heroic.vanilla.armour", () -> {
            Multimap<Attribute, AttributeModifier> mods = chestMeta.getAttributeModifiers(EquipmentSlot.CHEST);
            if (mods == null || mods.isEmpty()) {
                throw new IllegalStateException("no CHEST attribute modifiers were written — armour not re-statted");
            }
            // Real diamond chestplate values: 8 armour + 2 toughness. Match by amount (version-agnostic; avoids a
            // renamed Attribute constant) — a sub-diamond gold piece has no other CHEST modifiers.
            double armour = HeroicDiamond.diamondArmourPoints(Material.GOLDEN_CHESTPLATE);       // 8
            double toughness = HeroicDiamond.diamondArmourToughness(Material.GOLDEN_CHESTPLATE); // 2
            if (!hasAmount(mods, armour) || !hasAmount(mods, toughness)) {
                throw new IllegalStateException("CHEST modifiers must include diamond armour " + armour
                        + " and toughness " + toughness + ", got " + mods.values());
            }
        });

        h.guard("heroic.vanilla.weapon", () -> {
            Multimap<Attribute, AttributeModifier> mods = swordMeta.getAttributeModifiers(EquipmentSlot.HAND);
            if (mods == null || mods.isEmpty()) {
                throw new IllegalStateException("no HAND attribute modifiers were written — weapon not re-statted");
            }
            // Diamond sword attack modifier = total (7) − player base (1.0) = 6.
            double modifier = HeroicDiamond.diamondAttackDamage(Material.GOLDEN_SWORD) - 1.0; // 6
            if (!hasAmount(mods, modifier)) {
                throw new IllegalStateException("HAND modifiers must include the diamond attack modifier " + modifier
                        + ", got " + mods.values());
            }
        });

        h.guard("heroic.vanilla.visibleTooltip", () -> {
            // The explicit modifiers suppress the gold defaults, so the vanilla tooltip renders the diamond values;
            // HIDE_ATTRIBUTES must NOT be set (ADR-0032 removed it) or the whole block would vanish again.
            if (chestMeta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES) || swordMeta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES)) {
                throw new IllegalStateException("HIDE_ATTRIBUTES must NOT be set — the vanilla attribute lines must show");
            }
        });

        h.guard("heroic.vanilla.marker", () -> {
            // The neutral marker names the diamond each gold piece stands in for, so Mental treats it as diamond.
            String chestMark = marker(chestMeta);
            String swordMark = marker(swordMeta);
            if (!"DIAMOND_CHESTPLATE".equals(chestMark) || !"DIAMOND_SWORD".equals(swordMark)) {
                throw new IllegalStateException("combat:effective_material must be DIAMOND_CHESTPLATE / DIAMOND_SWORD, got "
                        + chestMark + " / " + swordMark);
            }
        });

        h.guard("heroic.vanilla.durability", () -> {
            int diamondMax = HeroicDiamond.diamondDurability(Material.GOLDEN_CHESTPLATE); // 528
            int goldMax = Material.GOLDEN_CHESTPLATE.getMaxDurability();
            int custom = customMaxDurability(chestMeta); // -1 when the platform has no custom max (pre-1.20.5)
            int actualMax = custom >= 0 ? custom : goldMax;
            int expected = supportsMaxDamageOverride() ? diamondMax : goldMax;
            if (actualMax != expected) {
                throw new IllegalStateException("max durability should be " + expected + ", was " + actualMax);
            }
        });
    }

    /** A 100%-success heroic service that swaps diamond gear to its gold twin with vanilla-stats ON. */
    private HeroicService service() {
        HeroicConfig cfg = new HeroicConfig("NETHERITE_SCRAP", "&6Heroic", List.of(), 100, 100,
                0.10, 0.10, 0.20,
                Map.of("DIAMOND_CHESTPLATE", "GOLDEN_CHESTPLATE", "DIAMOND_SWORD", "GOLDEN_SWORD"),
                "ENTITY", "", false, true /* diamond-stats */, true /* vanilla-stats */);
        HeroicUpgradeCodec upgrades = new HeroicUpgradeCodec(ItemKeys.of().heroicUpgrade());
        CombatCodec combat = new CombatCodec(ItemKeys.of().combat());
        LoreRenderer lore = new LoreRenderer(LoreStyle.DEFAULT, key -> null);
        return new HeroicService(upgrades, combat, lore, () -> cfg, new Random(), item.lang.Messages.defaults(),
                ItemGroups.standard());
    }

    private static ItemStack forge(HeroicService service, Material input, Material expectedDisplay) {
        HeroicResult result = service.applyTo(service.mint(), new ItemStack(input));
        if (!result.commit() || result.newTarget() == null) {
            throw new IllegalStateException("a 100% heroic roll should succeed and produce a piece");
        }
        ItemStack forged = result.newTarget();
        if (forged.getType() != expectedDisplay) {
            throw new IllegalStateException("the heroic display should be " + expectedDisplay + ", was " + forged.getType());
        }
        return forged;
    }

    private static boolean hasAmount(Multimap<Attribute, AttributeModifier> mods, double amount) {
        for (AttributeModifier mod : mods.values()) {
            if (mod.getAmount() == amount) {
                return true;
            }
        }
        return false;
    }

    private static String marker(ItemMeta meta) {
        if (EFFECTIVE_MATERIAL == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(EFFECTIVE_MATERIAL, PersistentDataType.STRING);
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
