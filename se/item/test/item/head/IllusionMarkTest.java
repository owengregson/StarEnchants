package item.head;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import item.codec.ItemKeys;
import item.codec.ItemStateStore;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/** The illusion marker's contract (ADR-0064): detect always; undress only from an intact payload. */
class IllusionMarkTest {

    /** Identity-keyed map store: mocked stacks carry no meta, so the fixture holds state beside them. */
    private static final class MapStore implements ItemStateStore {
        private final Map<ItemStack, Map<String, String>> state = new IdentityHashMap<>();

        @Override public String read(ItemStack stack, String key) {
            Map<String, String> tags = state.get(stack);
            return tags == null ? null : tags.get(key);
        }

        @Override public void write(ItemStack stack, String key, String blob) {
            state.computeIfAbsent(stack, s -> new HashMap<>()).put(key, blob);
        }

        @Override public boolean hasByte(ItemStack stack, String key) { return false; }
        @Override public void setByte(ItemStack stack, String key, boolean set) { }
        @Override public boolean hasInt(ItemStack stack, String key) { return false; }
        @Override public int readInt(ItemStack stack, String key, int dflt) { return dflt; }
        @Override public void writeInt(ItemStack stack, String key, int value) { }
    }

    /** Fake byte codec: serialization is a fixed token; deserialization returns a sentinel stack for it. */
    private static final class TokenBytes implements ItemBytes {
        final ItemStack restored = mock(ItemStack.class);
        private final byte[] token = "real-helmet".getBytes(StandardCharsets.UTF_8);

        @Override public byte[] serialize(ItemStack stack) {
            return token.clone();
        }

        @Override public ItemStack deserialize(byte[] bytes) {
            return Arrays.equals(bytes, token) ? restored : null;
        }
    }

    @Test
    void stampedHeadDetectsAndUndressesToTheSerializedHelmet() {
        MapStore store = new MapStore();
        TokenBytes bytes = new TokenBytes();
        IllusionMark mark = new IllusionMark(ItemKeys.of(), store, bytes);
        ItemStack shown = mock(ItemStack.class);

        mark.stamp(shown, mock(ItemStack.class));

        assertTrue(mark.isMarked(shown));
        assertSame(bytes.restored, mark.undress(shown));
    }

    @Test
    void noByteCodecStillDetectsButUndressReturnsNull() {
        MapStore store = new MapStore();
        IllusionMark mark = new IllusionMark(ItemKeys.of(), store, ItemBytes.NONE);
        ItemStack shown = mock(ItemStack.class);

        mark.stamp(shown, mock(ItemStack.class));

        assertTrue(mark.isMarked(shown)); // deny/detection still works without a payload
        assertNull(mark.undress(shown));
    }

    @Test
    void corruptPayloadNeverThrows() {
        MapStore store = new MapStore();
        IllusionMark mark = new IllusionMark(ItemKeys.of(), store, new TokenBytes());
        ItemStack forged = mock(ItemStack.class);
        store.write(forged, ItemKeys.of().illusion(), "!!! not base64 !!!");

        assertTrue(mark.isMarked(forged));
        assertNull(mark.undress(forged)); // corrupt → unrecoverable, never an exception on the click path
    }

    @Test
    void unmarkedStacksAreLeftAlone() {
        IllusionMark mark = new IllusionMark(ItemKeys.of(), new MapStore(), new TokenBytes());
        assertFalse(mark.isMarked(mock(ItemStack.class)));
        assertFalse(mark.isMarked(null));
        assertNull(mark.undress(mock(ItemStack.class)));
        assertNull(mark.undress(null));
    }
}
