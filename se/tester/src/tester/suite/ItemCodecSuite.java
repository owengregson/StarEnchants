package tester.suite;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.ItemKeys;
import tester.harness.Harness;

/**
 * Live checks for the on-item PDC codec (docs/architecture.md §4.2, §5.1). The pure blob format is
 * unit-tested; what only a real server can prove is that the blob survives in an {@code ItemStack}'s
 * PersistentDataContainer — and, crucially, that it persists through serialization identically
 * <em>across the spigot&rarr;mojang mapping flip</em> (§11; cross-version-item-api skill). PDC is
 * stable Bukkit API since 1.14, but the byte round-trip is verified, never assumed.
 *
 * <ul>
 *   <li>{@code item.codec.roundtrip} — write state to a real item, read it straight back.</li>
 *   <li>{@code item.codec.persist} — write, serialize()&rarr;deserialize(), read back equal (the save/load path).</li>
 *   <li>{@code item.codec.empty} — a plain item reads EMPTY, and writing EMPTY leaves it empty.</li>
 * </ul>
 */
public final class ItemCodecSuite implements Harness.Scenario {

    private final CombatCodec codec;

    public ItemCodecSuite(Plugin plugin) {
        this.codec = new CombatCodec(ItemKeys.of(plugin).combat());
    }

    @Override
    public void accept(Harness h) {
        h.expect("item.codec.roundtrip");
        h.expect("item.codec.persist");
        h.expect("item.codec.empty");

        Map<String, Integer> ench = new LinkedHashMap<>();
        ench.put("fire-aspect", 3);
        ench.put("soul_harvest", 5);
        CombatState state = new CombatState(ench, List.of("crit-crystal", "vampire-crystal"));

        h.guard("item.codec.roundtrip", () -> {
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            codec.write(sword, state);
            assertEqualState(state, codec.read(sword));
        });

        h.guard("item.codec.persist", () -> {
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            codec.write(sword, state);
            // The real save/load path: Bukkit serialization carries PDC across the mapping flip.
            Map<String, Object> serialized = sword.serialize();
            ItemStack restored = ItemStack.deserialize(serialized);
            assertEqualState(state, codec.read(restored));
        });

        h.guard("item.codec.empty", () -> {
            ItemStack plain = new ItemStack(Material.STICK);
            if (!codec.read(plain).isEmpty()) {
                throw new IllegalStateException("plain item reported non-empty combat state");
            }
            codec.write(plain, CombatState.EMPTY);
            if (!codec.read(plain).isEmpty()) {
                throw new IllegalStateException("writing EMPTY left a combat state behind");
            }
        });
    }

    private static void assertEqualState(CombatState expected, CombatState actual) {
        if (!expected.enchants().equals(actual.enchants())) {
            throw new IllegalStateException("enchants mismatch: " + expected.enchants() + " != " + actual.enchants());
        }
        if (!expected.crystals().equals(actual.crystals())) {
            throw new IllegalStateException("crystals mismatch: " + expected.crystals() + " != " + actual.crystals());
        }
    }
}
