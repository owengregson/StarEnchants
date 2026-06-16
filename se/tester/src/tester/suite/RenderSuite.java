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
 * Live checks for lore rendering from item state (docs/architecture.md §4.2). The line-building is
 * pure and unit-tested; what only a real server can prove is that {@code ItemMeta.setLore} accepts
 * the legacy §-coded lines and that {@code getLore} reads them back IDENTICALLY — and that they
 * survive serialization unchanged <em>across the spigot&rarr;mojang mapping flip</em> (a version
 * that rewrote legacy codes through a component layer would corrupt the round-trip). This suite is
 * item-only (no fake player), so it runs across the WHOLE range including the spigot-mapped floor.
 *
 * <ul>
 *   <li>{@code item.render.lore} — render state onto a real item, read the lore straight back.</li>
 *   <li>{@code item.render.persist} — render, serialize()&rarr;deserialize(), read back identical.</li>
 *   <li>{@code item.render.empty} — rendering empty state clears the managed lore.</li>
 * </ul>
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
            renderer.apply(sword, venom); // first give it managed lore
            renderer.apply(sword, CombatState.EMPTY); // then clear it from empty state
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
