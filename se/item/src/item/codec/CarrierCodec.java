package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

/**
 * Reads/writes a {@link CarrierData} on an item (ADR-0016), one PDC {@code STRING} under
 * {@link ItemKeys#carrier()} — separate from the {@link CombatCodec} blob so a carrier never decodes on
 * the combat hot path. Format {@code <itemKey>:<grantKey>:<grantLevel>[:<successBonus>[:<baseSuccess>]]};
 * content keys never contain {@code ':'}, so colons split fields cleanly. Trailing fields are positional:
 * the 4th (dust success bonus, ADR-0019) is omitted when zero and the 5th (base-success override, §I)
 * when {@code -1}, so pre-dust items encode byte-for-byte as 3 fields and old 3/4-field payloads decode
 * with {@code successBonus=0 / baseSuccess=-1} (no migration). A null/malformed entry decodes to
 * {@code null}, never throws. Also flags/clears the gear "guarded" marker ({@link ItemKeys#guarded()}).
 */
public final class CarrierCodec {

    private final NamespacedKey carrierKey;
    private final NamespacedKey guardedKey;

    public CarrierCodec(NamespacedKey carrierKey, NamespacedKey guardedKey) {
        this.carrierKey = carrierKey;
        this.guardedKey = guardedKey;
    }

    /** The carrier state on {@code stack}, or {@code null} if it is not a carrier. */
    public CarrierData read(ItemStack stack) {
        return decode(ItemBlobStore.read(stack, carrierKey));
    }

    public void write(ItemStack stack, CarrierData data) {
        ItemBlobStore.write(stack, carrierKey, data == null ? null : encode(data));
    }

    /** Whether {@code gear} carries a guard-scroll marker that spares it from a failed apply. */
    public boolean isGuarded(ItemStack gear) {
        return ItemFlagStore.hasByte(gear, guardedKey);
    }

    public void setGuarded(ItemStack gear, boolean guarded) {
        ItemFlagStore.setByte(gear, guardedKey, guarded);
    }

    static String encode(CarrierData data) {
        String base = data.itemKey() + ":" + data.grantKey() + ":" + data.grantLevel();
        // A base-success override (§I) forces the 5-field form (4th written as a placeholder if zero, since
        // fields are positional); else drop a zero bonus so pre-dust items encode byte-for-byte as 3 fields.
        if (data.hasBaseSuccess()) {
            return base + ":" + data.successBonus() + ":" + data.baseSuccess();
        }
        return data.successBonus() > 0 ? base + ":" + data.successBonus() : base;
    }

    static CarrierData decode(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split(":", -1); // -1: keep a trailing/empty grantKey segment
        if (parts.length < 3 || parts.length > 5 || parts[0].isEmpty()) {
            return null;
        }
        try {
            int level = Integer.parseInt(parts[2]);
            int successBonus = parts.length >= 4 ? Integer.parseInt(parts[3]) : 0; // legacy 3-field → 0
            int baseSuccess = parts.length == 5 ? Integer.parseInt(parts[4]) : -1; // pre-§I 3/4-field → -1
            return new CarrierData(parts[0], parts[1], level, successBonus, baseSuccess);
        } catch (NumberFormatException bad) {
            return null; // non-numeric level/bonus/base → treat as not-a-carrier, never crash
        }
    }
}
