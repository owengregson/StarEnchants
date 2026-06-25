package imagegen.fixture;

import item.codec.CombatState;
import item.render.LoreRenderer;
import item.render.LoreStyle;
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
