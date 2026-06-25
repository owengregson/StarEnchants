package tester.suite;

import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.ItemKeys;
import item.view.ItemView;
import item.view.ItemViewCache;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import tester.harness.Harness;

/**
 * The {@link ItemViewCache} (§5.2): reads the real blob back through an item's PDC (the copy-on-write meta the
 * design rests on) and keys on content. Thread-agnostic — PDC is safe from any thread — so these run inline.
 */
public final class ItemViewSuite implements Harness.Scenario {

    private final Plugin plugin;

    public ItemViewSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("itemview.decodeIdentity");
        h.expect("itemview.contentChange");

        CombatCodec codec = new CombatCodec(ItemKeys.of(plugin).combat());
        ItemViewCache cache = new ItemViewCache(codec, 0);

        h.guard("itemview.decodeIdentity", () -> {
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            CombatState written = new CombatState(Map.of("sharpness", 3), List.of("fire_crystal"));
            codec.write(sword, written);

            ItemView first = cache.of(sword);
            ItemView second = cache.of(sword);
            if (first != second) {
                throw new IllegalStateException("cache returned different views for identical item content");
            }
            if (!first.combat().equals(written)) {
                throw new IllegalStateException("decoded view " + first.combat() + " != written " + written);
            }
        });

        h.guard("itemview.contentChange", () -> {
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            codec.write(sword, new CombatState(Map.of("sharpness", 1), List.of()));
            ItemView before = cache.of(sword);

            codec.write(sword, new CombatState(Map.of("sharpness", 5), List.of()));
            ItemView after = cache.of(sword);

            if (before == after) {
                throw new IllegalStateException("cache served a stale view after the item content changed");
            }
            Integer level = after.combat().enchants().get("sharpness");
            if (level == null || level != 5) {
                throw new IllegalStateException("post-change view has wrong content: " + after.combat());
            }
        });
    }
}
