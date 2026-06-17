package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Reads/writes a {@link CarrierData} on an item (ADR-0016), stored as one PDC {@code STRING} under
 * {@link ItemKeys#carrier()} — SEPARATE from the {@link CombatCodec} blob so a carrier never decodes on
 * the combat hot path. Format {@code <itemKey>:<grantKey>:<grantLevel>}; content keys contain {@code /}
 * but never {@code ':'}, so the two {@code ':'}s split the three fields cleanly. Also flags/clears the
 * "guarded" protection marker on GEAR ({@link ItemKeys#guarded()}). A null/malformed entry decodes to
 * {@code null} (not a carrier), never an exception.
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
        return data.itemKey() + ":" + data.grantKey() + ":" + data.grantLevel();
    }

    static CarrierData decode(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split(":", -1); // -1: keep a trailing/empty grantKey segment
        if (parts.length != 3 || parts[0].isEmpty()) {
            return null;
        }
        try {
            return new CarrierData(parts[0], parts[1], Integer.parseInt(parts[2]));
        } catch (NumberFormatException bad) {
            return null; // non-numeric level → treat as not-a-carrier, never crash
        }
    }
}
