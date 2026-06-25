package tester.suite;

import item.codec.CombatState;
import item.render.LoreRenderer;
import item.render.LoreStyle;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tester.harness.Harness;

/**
 * Lore rendering from item state, live (docs/architecture.md §4.2). Only a real server proves that legacy
 * §-coded lines survive {@code setLore}/{@code getLore} and serialization round-trips identically across the
 * spigot&rarr;mojang mapping flip (a version routing legacy codes through a component layer would corrupt
 * them). Item-only (no fake player), so it runs the whole range including the spigot-mapped floor.
 */
public final class RenderSuite implements Harness.Scenario {

    private static final Function<String, String> NAMES = Map.of("enchants/venom", "Venom")::get;
    private static final List<String> EXPECTED = List.of("§7Venom §fIII");

    private final LoreRenderer renderer = new LoreRenderer(LoreStyle.DEFAULT, NAMES);

    @Override
    public void accept(Harness h) {
        h.expect("item.render.lore");
        h.expect("item.render.persist");
        h.expect("item.render.empty");

        CombatState venom = new CombatState(Map.of("enchants/venom", 3), List.of());

        h.guard("item.render.lore", () -> {
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            renderer.apply(sword, venom);
            assertLore(EXPECTED, loreOf(sword));
        });

        h.guard("item.render.persist", () -> {
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            renderer.apply(sword, venom);
            ItemStack restored = ItemStack.deserialize(sword.serialize());
            assertLore(EXPECTED, loreOf(restored));
        });

        h.guard("item.render.empty", () -> {
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            renderer.apply(sword, venom);
            renderer.apply(sword, CombatState.EMPTY);
            List<String> lore = loreOf(sword);
            if (lore != null && !lore.isEmpty()) {
                throw new IllegalStateException("empty state did not clear the lore: " + lore);
            }
        });
    }

    @SuppressWarnings("deprecation") // getLore(): deprecated-not-removed across the whole range.
    private static List<String> loreOf(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        return meta == null ? null : meta.getLore();
    }

    private static void assertLore(List<String> expected, List<String> actual) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException("lore mismatch: expected " + expected + " got " + actual);
        }
    }
}
