package feature.scroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import compile.load.ScrollsConfig;
import feature.apply.GestureOutcome;
import item.codec.AppliedSlot;
import item.codec.HolyProtectionCodec;
import item.codec.ItemKeys;
import item.codec.ItemStateStore;
import item.codec.ScrollCodec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import platform.item.ItemGroups;
import platform.lang.Messages;

/**
 * Holy-scroll corruption (§I): an item's allowance is spent by protections DELIVERED, and once exhausted the
 * item refuses further scrolls. The two halves are easy to get backwards — bumping on apply instead of on the
 * death that cashes it in would corrupt an item nobody ever died with — so both directions are pinned here.
 */
class HolyCorruptionTest {

    /** In-memory item state keyed by stack identity (the {@code item} module's FakeItemStateStore, locally). */
    private static final class MemoryState implements ItemStateStore {
        private final Map<ItemStack, Map<String, Object>> state = new IdentityHashMap<>();

        @Override public String read(ItemStack s, String k) {
            return at(s, k) instanceof String v ? v : null;
        }

        @Override public void write(ItemStack s, String k, String blob) {
            if (blob == null) {
                slot(s).remove(k);
            } else {
                slot(s).put(k, blob);
            }
        }

        @Override public boolean hasByte(ItemStack s, String k) { return at(s, k) instanceof Byte; }

        @Override public void setByte(ItemStack s, String k, boolean set) {
            if (set) {
                slot(s).put(k, (byte) 1);
            } else {
                slot(s).remove(k);
            }
        }

        @Override public boolean hasInt(ItemStack s, String k) { return at(s, k) instanceof Integer; }

        @Override public int readInt(ItemStack s, String k, int dflt) {
            return at(s, k) instanceof Integer v ? v : dflt;
        }

        @Override public void writeInt(ItemStack s, String k, int value) { slot(s).put(k, value); }

        private Map<String, Object> slot(ItemStack s) {
            return state.computeIfAbsent(s, k -> new HashMap<>());
        }

        private Object at(ItemStack s, String k) {
            Map<String, Object> slot = state.get(s);
            return slot == null ? null : slot.get(k);
        }
    }

    private final MemoryState state = new MemoryState();
    private final ItemKeys keys = ItemKeys.of();
    private final AppliedSlot slot = new AppliedSlot(keys.appliedSlot(), state);
    private final HolyProtectionCodec protections = new HolyProtectionCodec(keys.holyProtections(), state);
    private final List<ItemStack> reRendered = new ArrayList<>();

    /** A service over the shipped defaults (allowance 7, apply always succeeds). */
    private HolyScrollService service() {
        return new HolyScrollService(new ScrollCodec(keys.scroll(), keys.scrollConvert(), state), slot, protections,
                ScrollsConfig::defaults, new Random(1), Messages.defaults(), reRendered::add, ItemGroups.standard());
    }

    private ItemStack stack() {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(stack.getAmount()).thenReturn(1);
        return stack;
    }

    /** Apply a holy scroll to {@code gear}, then die with it in the drops — one full protect-and-cash-in cycle. */
    private void protectThenDie(HolyScrollService service, ItemStack gear) {
        service.applyTo(stack(), gear);
        List<ItemStack> drops = new ArrayList<>(List.of(gear));
        service.keepFromDrops(drops);
    }

    @Test
    void applyingAScrollSpendsNothingOfTheAllowance() {
        HolyScrollService service = service();
        ItemStack gear = stack();

        assertTrue(service.applyTo(stack(), gear).commit());

        assertTrue(slot.holds(gear, AppliedSlot.HOLY), "the keep marker is on the item");
        assertEquals(0, protections.count(gear), "…but nothing is spent until it actually saves the item");
    }

    @Test
    void aDeathThatCashesInTheMarkerSpendsExactlyOneProtection() {
        HolyScrollService service = service();
        ItemStack gear = stack();
        service.applyTo(stack(), gear);

        List<ItemStack> drops = new ArrayList<>(List.of(gear));
        List<ItemStack> kept = service.keepFromDrops(drops);

        assertEquals(List.of(gear), kept, "the item is pulled from the drops");
        assertTrue(drops.isEmpty());
        assertFalse(slot.holds(gear, AppliedSlot.HOLY), "the one-shot marker is consumed");
        assertEquals(1, protections.count(gear));
    }

    @Test
    void dyingWithNoMarkerSpendsNothing() {
        HolyScrollService service = service();
        ItemStack gear = stack();

        List<ItemStack> drops = new ArrayList<>(List.of(gear));
        assertTrue(service.keepFromDrops(drops).isEmpty());

        assertEquals(List.of(gear), drops, "an unprotected item drops as normal");
        assertEquals(0, protections.count(gear));
    }

    @Test
    void theSeventhProtectionCorruptsTheItemAndRefusesFurtherScrolls() {
        HolyScrollService service = service();
        ItemStack gear = stack();

        for (int i = 1; i <= 7; i++) {
            protectThenDie(service, gear);
            assertEquals(i, protections.count(gear), "protection " + i + " spent");
        }

        GestureOutcome refused = service.applyTo(stack(), gear);
        assertFalse(refused.commit(), "a corrupted item takes no more holy scrolls");
        assertFalse(refused.consumeCursor(), "…and the refused scroll is not consumed");
        assertFalse(slot.holds(gear, AppliedSlot.HOLY));
        assertEquals(7, protections.count(gear), "a refusal spends nothing further");
    }

    @Test
    void anItemShortOfTheAllowanceStillAcceptsScrolls() {
        HolyScrollService service = service();
        ItemStack gear = stack();
        for (int i = 0; i < 6; i++) {
            protectThenDie(service, gear);
        }

        assertTrue(service.applyTo(stack(), gear).commit(), "6 of 7 spent — very corrupt, but not yet refused");
    }

    @Test
    void aDeathReRendersTheItemSoTheLoreTracksTheNewStage() {
        HolyScrollService service = service();
        ItemStack gear = stack();

        protectThenDie(service, gear);

        // Twice: once stamping the HOLY PROTECTED line on apply, once swapping it for the corruption line.
        assertEquals(List.of(gear, gear), reRendered);
    }
}
