package feature.mask;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class MaskIllusionStoreTest {

    private static ItemStack cloningTo(ItemStack clone) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.clone()).thenReturn(clone);
        return stack;
    }

    @Test
    void roundTripClonesOnBothSides() {
        // put clones the caller's instance and get clones the stored one — a live template handed out could be
        // mutated by one recipient path and poison every later send.
        ItemStack served = mock(ItemStack.class);
        ItemStack storedCopy = cloningTo(served);
        ItemStack template = cloningTo(storedCopy);
        MaskIllusionStore store = new MaskIllusionStore();
        UUID wearer = UUID.randomUUID();

        store.put(wearer, template);

        assertSame(served, store.get(wearer)); // the stored copy's clone — never the template, never the copy
        assertTrue(store.wearers().contains(wearer));
    }

    @Test
    void removeReportsWhetherAnIllusionWasStored() {
        // remove()'s boolean drives the one-shot true-helmet restore broadcast: a false positive would spam
        // restores for never-masked players; a false negative would leave a popped mask rendered forever.
        MaskIllusionStore store = new MaskIllusionStore();
        UUID wearer = UUID.randomUUID();

        assertFalse(store.remove(wearer));

        store.put(wearer, cloningTo(mock(ItemStack.class)));
        assertTrue(store.remove(wearer));
        assertNull(store.get(wearer));
        assertFalse(store.remove(wearer));
    }

    @Test
    void quitAndDisableSweepsForget() {
        MaskIllusionStore store = new MaskIllusionStore();
        UUID quitter = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        store.put(quitter, cloningTo(mock(ItemStack.class)));
        store.put(other, cloningTo(mock(ItemStack.class)));

        store.clear(quitter); // the module's PlayerScoped quit sweep
        assertNull(store.get(quitter));
        assertTrue(store.wearers().contains(other));

        store.clearAll(); // module disable
        assertTrue(store.wearers().isEmpty());
    }
}
