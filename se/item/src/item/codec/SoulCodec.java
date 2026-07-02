package item.codec;

import java.util.UUID;
import org.bukkit.inventory.ItemStack;

/**
 * Reads/writes {@link SoulData} as one PDC {@code STRING} under {@link ItemKeys#soul()} (§6.3),
 * separate from the {@link CombatCodec} blob so a soul spend/gain never invalidates the content-hash
 * {@code ItemView} cache. Format {@code <gemId>:<souls>}; a UUID never contains {@code ':'}, so the
 * first {@code ':'} splits it. Null/malformed decodes to {@code null} (no gem), never throws.
 */
public final class SoulCodec {

    private final String soulKey;
    private final ItemStateStore store;

    public SoulCodec(String soulKey, ItemStateStore store) {
        this.soulKey = soulKey;
        this.store = store;
    }

    /** The gem state on {@code stack}, or {@code null} if it carries none. */
    public SoulData read(ItemStack stack) {
        return decode(store.read(stack, soulKey));
    }

    public void write(ItemStack stack, SoulData data) {
        store.write(stack, soulKey, data == null ? null : data.gemId() + ":" + data.souls());
    }

    /** Parse a {@code <gemId>:<souls>} payload, or {@code null} if absent/malformed. */
    static SoulData decode(String raw) {
        if (raw == null) {
            return null;
        }
        int sep = raw.indexOf(':');
        if (sep <= 0 || sep == raw.length() - 1) {
            return null;
        }
        try {
            return new SoulData(UUID.fromString(raw.substring(0, sep)), Integer.parseInt(raw.substring(sep + 1)));
        } catch (IllegalArgumentException bad) {
            return null; // bad UUID or non-numeric souls → no gem, never crash
        }
    }
}
