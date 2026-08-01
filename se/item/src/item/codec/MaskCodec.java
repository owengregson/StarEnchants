package item.codec;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.inventory.ItemStack;

/**
 * Marks / reads a MASK head item (ADR-0053). A mask stores its def key as a PDC {@code STRING} under
 * {@link ItemKeys#maskItem()} — presence <em>is</em> the identity, the {@link PetCodec}/{@link UseItemCodec}
 * shape, but with no counters: a mask never levels, so nothing rides its own integer keys. This is the
 * mask <em>item</em>'s identity before it is applied; once applied onto a helmet the key lives in that
 * helmet's combat blob ({@link CombatState#maskKey()}), not here.
 *
 * <p>Pure state, decode-tolerant: an absent/blank read is simply "not a mask", never a throw.
 */
public final class MaskCodec {

    public static final String MULTI_MASK_KEY = "masks/multi-mask";
    private static final String MULTI_PREFIX = MULTI_MASK_KEY + "|";

    private final String maskKey;
    private final ItemStateStore store;

    public MaskCodec(ItemKeys keys, ItemStateStore store) {
        this.maskKey = keys.maskItem();
        this.store = store;
    }

    public boolean isMask(ItemStack stack) {
        return keyOf(stack) != null;
    }

    /** The mask def key {@code stack} carries, or {@code null} if it is not a mask. */
    public String keyOf(ItemStack stack) {
        String raw = store.read(stack, maskKey);
        return raw == null || raw.isBlank() ? null : raw;
    }

    /** Stamp a mask item's identity (mint / admin give). */
    public void stamp(ItemStack stack, String defKey) {
        store.write(stack, maskKey, defKey);
    }

    /** Encode a compound Multi-Mask identity, preserving first occurrence order and rejecting nesting. */
    public static String multiKey(List<String> componentKeys) {
        Set<String> unique = new LinkedHashSet<>();
        for (String key : componentKeys == null ? List.<String>of() : componentKeys) {
            if (key != null && !key.isBlank() && !MULTI_MASK_KEY.equals(key) && !isMulti(key)) {
                unique.add(key);
            }
        }
        return unique.isEmpty() ? null : MULTI_PREFIX + String.join(",", unique);
    }

    public static boolean isMulti(String key) {
        return key != null && key.startsWith(MULTI_PREFIX);
    }

    /** The definition key used for likeness/validation; a compound resolves to the universal Multi-Mask def. */
    public static String definitionKey(String key) {
        return isMulti(key) ? MULTI_MASK_KEY : key;
    }

    /** Ordered component keys; a regular mask is represented as a one-element list. */
    public static List<String> components(String key) {
        if (key == null || key.isBlank()) {
            return List.of();
        }
        if (!isMulti(key)) {
            return List.of(key);
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String component : key.substring(MULTI_PREFIX.length()).split(",")) {
            if (!component.isBlank() && !MULTI_MASK_KEY.equals(component) && !isMulti(component)) {
                unique.add(component);
            }
        }
        return List.copyOf(unique);
    }
}
