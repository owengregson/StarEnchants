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
    void expFracRoundTripsClampsAndResetsOnStamp() {
        ItemStack stack = mock(ItemStack.class);
        assertEquals(0, codec.expFrac(stack)); // absent reads 0

        codec.stamp(stack, "cow", 3);
        codec.writeExpFrac(stack, 59_999);     // the largest legal carry (1/60000-exp units)
        assertEquals(59_999, codec.expFrac(stack));

        codec.writeExpFrac(stack, -4);         // corrupt writes clamp like the other counters
        assertEquals(0, codec.expFrac(stack));

        codec.writeExpFrac(stack, 123);
        codec.stamp(stack, "cow", 3);          // a re-stamp is a FRESH pet — no inherited carry
        assertEquals(0, codec.expFrac(stack));
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
