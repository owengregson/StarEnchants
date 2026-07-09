package platform.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import platform.caps.Capabilities;

/**
 * The pure cross-version band decision behind the use-item {@code is-food} eat gesture (the is-food design spec):
 * the eat needs 1.20.5+, on 1.21.2+ only the {@code consumable} component enables eating (so the 1.21.2/1.21.3
 * gap with no {@code consumable} API degrades to one-click), and below 1.20.5 the seam is disabled entirely. The
 * live reflective calls belong to the matrix (they need the 1.20.5+ types absent from the floor classpath); this
 * pins the version logic and the graceful-degrade contract without a server.
 */
class EdibleItemsTest {

    private static Capabilities at(String version) {
        return Capabilities.probe(version, false);
    }

    @Test
    void belowOneTwentyDotFiveIsAlwaysDisabled() {
        assertEquals(EdibleItems.Mode.NONE, EdibleItems.decide(at("1.20.4"), true, true));
        assertEquals(EdibleItems.Mode.NONE, EdibleItems.decide(at("1.17.1"), true, true));
        assertEquals(EdibleItems.Mode.NONE, EdibleItems.decide(at("1.8.9"), true, true));
    }

    @Test
    void theFoodBandCoversOneTwentyDotFiveThroughOneTwentyOneDotOne() {
        assertEquals(EdibleItems.Mode.FOOD_META, EdibleItems.decide(at("1.20.5"), true, false));
        assertEquals(EdibleItems.Mode.FOOD_META, EdibleItems.decide(at("1.20.6"), true, true));
        assertEquals(EdibleItems.Mode.FOOD_META, EdibleItems.decide(at("1.21.1"), true, false));
    }

    @Test
    void theConsumableBandCoversOneTwentyOneDotTwoOnward() {
        assertEquals(EdibleItems.Mode.CONSUMABLE_DATA, EdibleItems.decide(at("1.21.4"), false, true));
        assertEquals(EdibleItems.Mode.CONSUMABLE_DATA, EdibleItems.decide(at("1.21.11"), false, true));
        assertEquals(EdibleItems.Mode.CONSUMABLE_DATA, EdibleItems.decide(at("26.1.2"), true, true));
    }

    @Test
    void theOneTwentyOneDotTwoToThreeGapWithoutTheConsumableApiDegradesToOneClick() {
        // 1.21.2/1.21.3: food no longer enables eating and the consumable API is absent → no working seam.
        assertEquals(EdibleItems.Mode.NONE, EdibleItems.decide(at("1.21.2"), true, false));
        assertEquals(EdibleItems.Mode.NONE, EdibleItems.decide(at("1.21.3"), true, false));
    }

    @Test
    void aDisabledSeamIsAGracefulNoOp() {
        assertFalse(EdibleItems.NONE.enabled());
        ItemStack stack = mock(ItemStack.class);
        EdibleItems.NONE.makeEdible(stack);
        verifyNoInteractions(stack); // disabled → never mutates the item (the item stays a one-click use-item)
    }

    @Test
    void reflectionUnavailableAtTheFloorDegradesEvenOnAModernVersion() {
        // The 1.20.5+ food/consumable types are absent from the floor test classpath, so of() cannot resolve a
        // band and reports disabled — the same graceful degrade a server missing the API would get.
        assertFalse(EdibleItems.of(at("1.21.4")).enabled());
    }
}
