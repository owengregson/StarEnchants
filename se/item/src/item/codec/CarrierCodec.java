package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Reads/writes a {@link CarrierData} on an item (ADR-0016), stored as one PDC {@code STRING} under
 * {@link ItemKeys#carrier()} — SEPARATE from the {@link CombatCodec} blob so a carrier never decodes on
 * the combat hot path. Format {@code <itemKey>:<grantKey>:<grantLevel>[:<successBonus>]}; content keys
 * contain {@code /} but never {@code ':'}, so the colons split the fields cleanly. The 4th field (a
 * dust-accumulated success bonus, ADR-0019) is OMITTED when zero, so every pre-dust item encodes
 * byte-for-byte as before and an old 3-field payload decodes with {@code successBonus = 0} (no
 * migration). Also flags/clears the "guarded" protection marker on GEAR ({@link ItemKeys#guarded()}).
 * A null/malformed entry decodes to {@code null} (not a carrier), never an exception.
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
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return decode(stack.getItemMeta().getPersistentDataContainer().get(carrierKey, PersistentDataType.STRING));
    }

    /** Mark {@code stack} as the carrier {@code data} (clearing the marker when {@code null}). */
    public void write(ItemStack stack, CarrierData data) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (data == null) {
            pdc.remove(carrierKey);
        } else {
            pdc.set(carrierKey, PersistentDataType.STRING, encode(data));
        }
        stack.setItemMeta(meta);
    }

    /** Whether {@code gear} carries a guard-scroll protection marker (spares it from a failed apply). */
    public boolean isGuarded(ItemStack gear) {
        if (gear == null || !gear.hasItemMeta()) {
            return false;
        }
        Byte flag = gear.getItemMeta().getPersistentDataContainer().get(guardedKey, PersistentDataType.BYTE);
        return flag != null && flag != 0;
    }

    /** Set or clear the guard-scroll protection marker on {@code gear}. */
    public void setGuarded(ItemStack gear, boolean guarded) {
        ItemMeta meta = gear.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (guarded) {
            pdc.set(guardedKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            pdc.remove(guardedKey);
        }
        gear.setItemMeta(meta);
    }

    static String encode(CarrierData data) {
        String base = data.itemKey() + ":" + data.grantKey() + ":" + data.grantLevel();
        // Omit the success-bonus field when zero so a freshly-minted carrier (and every pre-dust item)
        // encodes byte-for-byte as the original 3-field format — no version churn for the common case.
        return data.successBonus() > 0 ? base + ":" + data.successBonus() : base;
    }

    static CarrierData decode(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split(":", -1); // -1: keep a trailing/empty grantKey segment
        if ((parts.length != 3 && parts.length != 4) || parts[0].isEmpty()) {
            return null;
        }
        try {
            int level = Integer.parseInt(parts[2]);
            int successBonus = parts.length == 4 ? Integer.parseInt(parts[3]) : 0; // legacy 3-field → 0
            return new CarrierData(parts[0], parts[1], level, successBonus);
        } catch (NumberFormatException bad) {
            return null; // non-numeric level/bonus → treat as not-a-carrier, never crash
        }
    }
}
