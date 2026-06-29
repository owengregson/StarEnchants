package imagegen.fixture;

import item.codec.CombatState;
import item.render.LoreRenderer;
import item.render.LoreStyle;
import item.render.TextWrap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The in-repo, deterministic preview set. Item tooltips reuse the plugin's own {@link LoreRenderer} so a rendered
 * tooltip is exactly the lore the live plugin would stamp — the previews can't drift from the engine. GUI fixtures
 * mirror the plugin's {@code feature.menu.MenuLayout} geometry (6-row paged chest, nav row reserved). Display names
 * are a small fixed map standing in for the live catalog; everything here is illustrative content, not real config.
 */
public final class Fixtures {

    private Fixtures() {
    }

    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("venom", "Venom"),
            Map.entry("lifesteal", "Life Steal"),
            Map.entry("rage", "Rage"),
            Map.entry("frost", "Frost"),
            Map.entry("vampire", "Vampire"),
            Map.entry("guardian", "Guardian"),
            Map.entry("berserk", "Berserk"));

    private static final LoreRenderer LORE = new LoreRenderer(LoreStyle.DEFAULT, NAMES::get);

    /** Item-tooltip previews: a fully-kitted weapon, a set armour piece, and an enchantment book. */
    public static List<ItemFixture> tooltips() {
        return List.of(
                new ItemFixture("tooltip-weapon", "DIAMOND_SWORD", "&bChampion's Blade",
                        LORE.lines(new CombatState(enchants("venom", 5, "lifesteal", 3, "rage", 2),
                                List.of("frost")))),
                new ItemFixture("tooltip-armor", "DIAMOND_CHESTPLATE", "&9Guardian Plating",
                        LORE.lines(new CombatState(enchants("guardian", 4), List.of(), "guardian", false))),
                new ItemFixture("tooltip-book", "ENCHANTED_BOOK", "&dEnchantment Book",
                        LORE.lines(new CombatState(enchants("vampire", 3), List.of()))));
    }

    /**
     * Elite-Enchantments item-tooltip previews (§I overhaul). The economy items are wrapped through the plugin's
     * own {@link TextWrap} at the universal {@code lore.item-wrap} width (30) exactly as {@code ItemFactory}
     * does at mint, so each preview matches the live tooltip; placeholders are pre-substituted to representative
     * values. The enchant book reuses the enchant-book likeness (description wrapped at its own {@code wrap=30}).
     */
    public static List<ItemFixture> eeItems() {
        return List.of(
                ee("tooltip-ee-white-scroll", "PAPER", "&f&lWhite Scroll", List.of(
                        "&a100% Success Rate",
                        "&c0% Failure Rate",
                        "",
                        "&eProtects an item from being destroyed due to a failed Enchantment Book. This scroll will be consumed and removed from the item when an enchant fails.",
                        "&7Drag n' Drop on an item to apply.")),
                ee("tooltip-ee-holy-white-scroll", "MAP", "&6&lHoly White Scroll", List.of(
                        "&eA legendary item that protects an item from being lost from the inventory upon death. This scroll will be consumed and removed from the item upon death.",
                        "&7Drag n' Drop on an item to apply.")),
                ee("tooltip-ee-black-scroll", "INK_SAC", "&8&lBlack Scroll", List.of(
                        "",
                        "&675% Success Rate",
                        "",
                        "&eRemoves a random enchantment from your item and returns it to your inventory with the specified success rate.",
                        "&7Drag n' Drop on an item to apply.")),
                ee("tooltip-ee-transmog-scroll", "PAPER", "&c&lTransmog Scroll", List.of(
                        "&eOrganizes enchants by &f&nrarity&r&e on your item and adds the &denchant &bcount &eto the name.",
                        "&7Drag n' Drop on an item to apply.")),
                ee("tooltip-ee-enchant-orb", "ENDER_EYE", "&6&lEnchantment Orb [&r&a&n+5&6&l]", List.of(
                        "&a100% Success Rate",
                        "&c0% Failure Rate",
                        "",
                        "&6+5 Enchantment Slot(s)",
                        "",
                        "&eIncreases the number of enchantment slots on an item by 5, up to a maximum of 15.",
                        "&7Drag n' Drop on an item to apply.")),
                ee("tooltip-ee-item-nametag", "NAME_TAG", "&6&lItem Nametag", List.of(
                        "&eAllows you to rename and customize your equipment with customizable color and formatting codes.",
                        "&7Drag n' Drop on an item to apply.")),
                ee("tooltip-ee-blocktrak-gem", "SLIME_BALL", "&a&lBlockTrak Gem", List.of(
                        "&eDisplays the amount of blocks broken with the tool since it was forged.",
                        "",
                        "&eApplies to: &r&f&nTool",
                        "&7Drag n' Drop on an item to apply.")),
                ee("tooltip-ee-mobtrak-gem", "MAGMA_CREAM", "&e&lMobTrak Gem", List.of(
                        "&eDisplays the amount of mobs slain with the weapon since it was forged.",
                        "",
                        "&eApplies to: &r&f&nWeapon",
                        "&7Drag n' Drop on an item to apply.")),
                ee("tooltip-ee-soultrak-gem", "FIRE_CHARGE", "&c&lSoulTrak Gem", List.of(
                        "&eDisplays the amount of players killed with the weapon since it was forged.",
                        "",
                        "&eApplies to: &r&f&nWeapon",
                        "&7Drag n' Drop on an item to apply.")),
                ee("tooltip-ee-fishtrak-gem", "CLAY_BALL", "&3&lFishTrak Gem", List.of(
                        "&eDisplays the amount of fish caught with the rod since it was forged.",
                        "",
                        "&eApplies to: &r&f&nFishing Rod",
                        "&7Drag n' Drop on an item to apply.")),
                ee("tooltip-ee-soul-gem", "EMERALD", "&c&lSoul Gem [&r&b&n&l256&r&c&l]", List.of(
                        "",
                        "&c* &7Right Click while holding this item to toggle &c&nSoul Mode.",
                        "",
                        "&c* &7While in Soul Mode, your equipped soul enchantments can activate and drain souls for as long as this mode is enabled.",
                        "",
                        "&c* &7Use &c&n/splitsouls&r &7on this item to split souls off of it.",
                        "",
                        "&c* &7Stack other &c&nSoul Gems&r &7on top of this one to combine their soul counts.")),
                divineImmolationBook());
    }

    /** An economy-item fixture: lore wrapped exactly as the live mint path wraps it (lore.item-wrap = 30). */
    private static ItemFixture ee(String id, String material, String name, List<String> rawLore) {
        return new ItemFixture(id, material, name, TextWrap.wrapAll(rawLore, 30));
    }

    /**
     * A Divine Immolation IV enchant book (soul tier → {@code &c}), rendered through the enchant-book likeness:
     * the success/failure rate sits right below the name, then a blank line, the description ({@code \n}-joined,
     * wrapped at the book's own {@code wrap=30} and tier-coloured), the applies-to ("Sword Enchantment"), and
     * the drag footer.
     */
    private static ItemFixture divineImmolationBook() {
        String desc = String.join("\n",
                "&cOn hit, ignites the target,",
                "&cdeals bonus damage and applies",
                "&cWither.",
                "",
                "&c&lI: &f▲ 10% &7| &f⌛ 2s &7| &f☄ 20",
                "&c&lII: &f▲ 15% &7| &f⌛ 2s &7| &f☄ 20",
                "&c&lIII: &f▲ 20% &7| &f⌛ 2s &7| &f☄ 20",
                "&c&lIV: &f▲ 25% &7| &f⌛ 2s &7| &f☄ 20");
        List<String> lore = new ArrayList<>();
        lore.add("&a100% Success Rate"); // rates sit right below the name
        lore.add("&c0% Failure Rate");
        lore.add("");
        for (String line : TextWrap.wrap(desc, 30)) {
            lore.add("&c" + line); // {TIER_COLOR}{DESCRIPTION}
        }
        lore.add("");
        lore.add("&7Sword Enchantment");
        lore.add("&7Drag n' Drop on an item to apply.");
        return new ItemFixture("tooltip-ee-divine-immolation-book", "ENCHANTED_BOOK",
                "&c&l&nDivine Immolation IV", lore);
    }

    /** GUI previews: the enchant-application menu, hovered on a book so its tooltip shows. */
    public static List<MenuFixture> menus() {
        Map<Integer, SlotFixture> slots = new LinkedHashMap<>();
        // Content: a row of purchasable enchant books.
        slots.put(11, book("&aVenom", "&7Adds &aVenom &7to your weapon.", "&7Cost: &a1,000"));
        slots.put(13, book("&bLife Steal", "&7Heal on hit.", "&7Cost: &a2,500"));
        slots.put(15, book("&cRage", "&7Build fury as you fight.", "&7Cost: &a1,750"));
        slots.put(29, book("&9Guardian", "&7Reduce incoming damage.", "&7Cost: &a3,000"));
        slots.put(31, book("&dVampire", "&7Leech life from foes.", "&7Cost: &a2,000"));
        slots.put(33, book("&eBerserk", "&7Hit harder at low health.", "&7Cost: &a2,250"));
        // Nav row (slots 45..53): buttons over a glass-pane backing.
        for (int s = 45; s <= 53; s++) {
            slots.put(s, SlotFixture.of("GRAY_STAINED_GLASS_PANE", " "));
        }
        slots.put(45, nav("ARROW", "&aPrevious Page"));
        slots.put(53, nav("ARROW", "&aNext Page"));
        slots.put(48, nav("ARROW", "&cBack"));
        slots.put(49, nav("BARRIER", "&cClose"));

        return List.of(new MenuFixture("gui-enchanter", 6, "&8Enchanter", null, slots, 13));
    }

    private static SlotFixture book(String name, String... lore) {
        return new SlotFixture("ENCHANTED_BOOK", 1, name, List.of(lore));
    }

    private static SlotFixture nav(String material, String name) {
        return new SlotFixture(material, 1, name, List.of());
    }

    /** Build an order-preserving enchant map from {@code key, level, key, level, …} pairs. */
    private static Map<String, Integer> enchants(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }
}
