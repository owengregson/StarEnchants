package item.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/** The pet identity/level/exp + Pet Food codec (ADR-0052) over the fake state store. */
class PetCodecTest {

    private final PetCodec codec = new PetCodec(ItemKeys.of(), new FakeItemStateStore());

    @Test
    void stampAndReadBackIdentityLevelAndExp() {
        ItemStack stack = mock(ItemStack.class);
        assertFalse(codec.isPet(stack));
        assertNull(codec.keyOf(stack));

        codec.stamp(stack, "shield", 25);
        assertTrue(codec.isPet(stack));
        assertEquals("shield", codec.keyOf(stack));
        assertEquals(25, codec.level(stack));
        assertEquals(0, codec.exp(stack));

        codec.writeProgress(stack, 26, 40);
        assertEquals(26, codec.level(stack));
        assertEquals(40, codec.exp(stack));
    }

    @Test
    void absentOrCorruptCountersReadAsSafeDefaults() {
        ItemStack stack = mock(ItemStack.class);
        codec.stamp(stack, "cat", 0);          // sub-1 stamp clamps to 1
        assertEquals(1, codec.level(stack));
        codec.writeProgress(stack, -5, -9);    // corrupt writes clamp too
        assertEquals(1, codec.level(stack));
        assertEquals(0, codec.exp(stack));
    }

    @Test
    void petFoodBakesItsGrantAndAVanillaCarrotIsNotFood() {
        ItemStack food = mock(ItemStack.class);
        assertFalse(codec.isPetFood(food));
        assertEquals(0, codec.foodLevels(food));

        codec.stampFood(food, 10);
        assertTrue(codec.isPetFood(food));
        assertEquals(10, codec.foodLevels(food));
        assertFalse(codec.isPet(food)); // food is never a pet
    }
}
