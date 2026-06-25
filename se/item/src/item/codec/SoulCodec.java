package item.codec;

import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Reads/writes {@link SoulData} as one PDC {@code STRING} under {@link ItemKeys#soul()} (§6.3),
 * separate from the {@link CombatCodec} blob so a soul spend/gain never invalidates the content-hash
 * {@code ItemView} cache. Format {@code <gemId>:<souls>}; a UUID never contains {@code ':'}, so the
 * first {@code ':'} splits it. Null/malformed decodes to {@code null} (no gem), never throws.
 */
public final class SoulCodec {

    private final NamespacedKey soulKey;

    public SoulCodec(NamespacedKey soulKey) {
        this.soulKey = soulKey;
    }

    /** The gem state on {@code stack}, or {@code null} if it carries none. */
    public SoulData read(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return decode(stack.getItemMeta().getPersistentDataContainer().get(soulKey, PersistentDataType.STRING));
    }

    public void write(ItemStack stack, SoulData data) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (data == null) {
            pdc.remove(soulKey);
        } else {
            pdc.set(soulKey, PersistentDataType.STRING, data.gemId() + ":" + data.souls());
        }
        stack.setItemMeta(meta);
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
