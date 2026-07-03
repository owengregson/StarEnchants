package item.mint;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * Server-free tests for the vanilla-enchant applier (ADR-0047 instance wiring). The meta-touching enchant
 * application is exercised live (like the former {@code ItemFactory.applyVanillaEnchants}); here we pin the
 * guard branches and that a resolver miss is silently skipped, never throwing — so a bad set-piece enchant
 * name can never crash a mint.
 */
final class VanillaEnchantsTest {

    @Test
    void noneIsANoOpOnANullStack() {
        assertDoesNotThrow(() -> VanillaEnchants.NONE.apply(null, Map.of("SHARPNESS", 1)));
    }

    @Test
    void nullOrEmptyMapDoesNothing() {
        ItemStack stack = new ItemStack(Material.DIAMOND_SWORD);
        assertDoesNotThrow(() -> VanillaEnchants.NONE.apply(stack, null));
        assertDoesNotThrow(() -> VanillaEnchants.NONE.apply(stack, Map.of()));
    }

    @Test
    void aResolverMissIsConsultedThenSkippedWithoutThrowing() {
        AtomicBoolean consulted = new AtomicBoolean(false);
        VanillaEnchants miss = new VanillaEnchants(name -> {
            consulted.set(true);
            return null; // every name misses → nothing is applied, no meta is touched
        });
        ItemStack stack = new ItemStack(Material.DIAMOND_SWORD);
        assertDoesNotThrow(() -> miss.apply(stack, Map.of("SHARPNESS", 3)));
        assertTrue(consulted.get(), "the resolver is consulted for a present, non-empty mapping");
    }

    @Test
    void nullResolverDefaultsToNoOp() {
        ItemStack stack = new ItemStack(Material.DIAMOND_SWORD);
        assertDoesNotThrow(() -> new VanillaEnchants(null).apply(stack, Map.of("SHARPNESS", 1)));
    }
}
