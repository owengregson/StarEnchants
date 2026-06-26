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
 * The on-item PDC codec (§4.2, §5.1). The blob format is unit-tested; only a real server proves it survives
 * an {@code ItemStack}'s PDC through serialization identically across the spigot→mojang mapping flip (§11).
 */
public final class ItemCodecSuite implements Harness.Scenario {

    private final CombatCodec codec;

    public ItemCodecSuite(Plugin plugin) {
        this.codec = new CombatCodec(ItemKeys.of().combat());
    }

    @Override
    public void accept(Harness h) {
        h.expect("item.codec.roundtrip");
        h.expect("item.codec.persist");
        h.expect("item.codec.empty");

        Map<String, Integer> ench = new LinkedHashMap<>();
        ench.put("fire-aspect", 3);
        ench.put("soul_harvest", 5);
        CombatState state = new CombatState(ench, List.of("crit-crystal", "vampire-crystal"), "sets/yeti", true);

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
        if (!java.util.Objects.equals(expected.setKey(), actual.setKey())) {
            throw new IllegalStateException("setKey mismatch: " + expected.setKey() + " != " + actual.setKey());
        }
        if (expected.omni() != actual.omni()) {
            throw new IllegalStateException("omni mismatch: " + expected.omni() + " != " + actual.omni());
        }
    }
}
