package item.head;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The modern {@link HeadEquip}: overrides the head's {@code minecraft:equippable} data component so the CLIENT
 * itself refuses the helmet slot (1.21.2+). A player head carries a DEFAULT equippable component with
 * {@code slot=head} — precisely what makes a bare head helmet-wearable — so replacing it wholesale with a
 * non-head slot ({@code mainhand}) plus {@code swappable=false}/{@code dispensable=false} makes vanilla's own
 * armour-slot predicate ({@code Slot#mayPlace}) reject it. Direct place, shift-click auto-equip, hotbar swap,
 * right-click auto-equip and dispenser-equip all then fall through without a server round-trip. Wholesale
 * override (not {@code setEquippable(null)} removal) so the outcome is deterministic regardless of how a given
 * build handles removing a material-default component.
 *
 * <p>The entire equippable-component surface is a 1.21.2+ item API absent from the 1.17.1 compile floor, so
 * every access is a once-probed {@link MethodHandle} (the {@code ModernHeadAttributes} precedent). Below 1.21.2
 * the probes miss and this degrades to a no-op — the server-side {@code HeadEquipGuard} carries the guard alone
 * on that lane. Pure item decoration — safe from any thread.
 */
public final class ModernHeadEquip implements HeadEquip {

    private static final Logger LOG = System.getLogger("StarEnchants.HeadEquip");

    /** {@code org.bukkit.inventory.meta.components.EquippableComponent} — the 1.21.2+ component type, else null. */
    private static final Class<?> COMPONENT = probeComponent();

    /** {@code ItemMeta#getEquippable()/setEquippable(EquippableComponent)} — the 1.21.2+ accessor pair. */
    private static final MethodHandle GET_EQUIPPABLE = COMPONENT == null ? null
            : metaHandle("getEquippable", MethodType.methodType(COMPONENT));
    private static final MethodHandle SET_EQUIPPABLE = COMPONENT == null ? null
            : metaHandle("setEquippable", MethodType.methodType(void.class, COMPONENT));

    /** {@code EquippableComponent#setSlot/setSwappable/setDispensable} — the neutralising mutators. */
    private static final MethodHandle SET_SLOT = COMPONENT == null ? null
            : componentHandle("setSlot", MethodType.methodType(void.class, EquipmentSlot.class));
    private static final MethodHandle SET_SWAPPABLE = COMPONENT == null ? null
            : componentHandle("setSwappable", MethodType.methodType(void.class, boolean.class));
    private static final MethodHandle SET_DISPENSABLE = COMPONENT == null ? null
            : componentHandle("setDispensable", MethodType.methodType(void.class, boolean.class));

    @Override
    public void unwearable(ItemStack stack) {
        if (stack == null || GET_EQUIPPABLE == null || SET_EQUIPPABLE == null || SET_SLOT == null) {
            return; // pre-1.21.2 (component absent) — the server-side guard carries it
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        try {
            Object component = GET_EQUIPPABLE.invoke(meta);
            SET_SLOT.invoke(component, EquipmentSlot.HAND); // a slot the player's inventory has no armour cell for
            if (SET_SWAPPABLE != null) {
                SET_SWAPPABLE.invoke(component, false); // kill the right-click auto-equip
            }
            if (SET_DISPENSABLE != null) {
                SET_DISPENSABLE.invoke(component, false); // kill the dispenser-equip
            }
            SET_EQUIPPABLE.invoke(meta, component);
            stack.setItemMeta(meta);
        } catch (Throwable failure) {
            // A cosmetic client hint only — a per-version component quirk must never break the mint itself.
            LOG.log(Level.DEBUG, "equippable neutralisation skipped", failure);
        }
    }

    private static Class<?> probeComponent() {
        try {
            return Class.forName("org.bukkit.inventory.meta.components.EquippableComponent");
        } catch (ClassNotFoundException absent) {
            LOG.log(Level.DEBUG, "EquippableComponent absent (pre-1.21.2) — the client head-equip hint degrades");
            return null;
        }
    }

    private static MethodHandle metaHandle(String name, MethodType type) {
        try {
            return MethodHandles.publicLookup().findVirtual(ItemMeta.class, name, type);
        } catch (NoSuchMethodException | IllegalAccessException absent) {
            LOG.log(Level.DEBUG, "ItemMeta." + name + " absent — the client head-equip hint degrades");
            return null;
        }
    }

    private static MethodHandle componentHandle(String name, MethodType type) {
        try {
            return MethodHandles.publicLookup().findVirtual(COMPONENT, name, type);
        } catch (NoSuchMethodException | IllegalAccessException absent) {
            LOG.log(Level.DEBUG, "EquippableComponent." + name + " absent — the client head-equip hint degrades");
            return null;
        }
    }
}
