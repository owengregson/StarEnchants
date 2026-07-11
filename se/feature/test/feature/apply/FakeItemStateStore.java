package feature.apply;

import item.codec.ItemStateStore;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import org.bukkit.inventory.ItemStack;

/**
 * In-memory {@link ItemStateStore} for feature-layer unit tests (ADR-0044): associates state with an
 * {@link ItemStack} by identity, so an enchanter/service PDC round-trip is exercisable without a server.
 * Mirrors the item-module test fixture of the same name (a module's test sources are not visible across
 * module boundaries, so the ~60 lines are duplicated rather than a testFixtures publication added for them).
 * A test stack may be a plain mock — the store never calls methods on it.
 */
public final class FakeItemStateStore implements ItemStateStore {

    private final Map<ItemStack, Map<String, Object>> state = new IdentityHashMap<>();

    @Override
    public String read(ItemStack stack, String logicalKey) {
        Object v = at(stack, logicalKey);
        return v instanceof String ? (String) v : null;
    }

    @Override
    public void write(ItemStack stack, String logicalKey, String blob) {
        if (blob == null) {
            slot(stack).remove(logicalKey);
        } else {
            slot(stack).put(logicalKey, blob);
        }
    }

    @Override
    public boolean hasByte(ItemStack stack, String logicalKey) {
        return at(stack, logicalKey) instanceof Byte;
    }

    @Override
    public void setByte(ItemStack stack, String logicalKey, boolean set) {
        if (set) {
            slot(stack).put(logicalKey, (byte) 1);
        } else {
            slot(stack).remove(logicalKey);
        }
    }

    @Override
    public boolean hasInt(ItemStack stack, String logicalKey) {
        return at(stack, logicalKey) instanceof Integer;
    }

    @Override
    public int readInt(ItemStack stack, String logicalKey, int dflt) {
        Object v = at(stack, logicalKey);
        return v instanceof Integer ? (Integer) v : dflt;
    }

    @Override
    public void writeInt(ItemStack stack, String logicalKey, int value) {
        slot(stack).put(logicalKey, value);
    }

    private Map<String, Object> slot(ItemStack stack) {
        return state.computeIfAbsent(stack, k -> new HashMap<>());
    }

    private Object at(ItemStack stack, String logicalKey) {
        Map<String, Object> slot = state.get(stack);
        return slot == null ? null : slot.get(logicalKey);
    }
}
