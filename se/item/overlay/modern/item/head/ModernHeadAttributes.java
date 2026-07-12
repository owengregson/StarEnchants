package item.head;

import com.google.common.collect.Multimap;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The modern {@link HeadAttributes}: explicit helmet modifiers copy verbatim (heroic's real stats, ADR-0031);
 * a modifier-less vanilla helmet contributes its material's HEAD-slot defaults via
 * {@code Material#getDefaultAttributeModifiers(EquipmentSlot)} — a 1.18+ API name-probed once (Bukkit surface,
 * mapping-flip immune); on the 1.17.1 floor exactly, default lines degrade away while explicit ones still copy.
 *
 * <p>Also mirrors the helmet's durability: a player head has no durability of its own, so the shown head gets
 * the helmet's {@code max_damage} + {@code damage} components — the client (and durability-display mods) then
 * render the real helmet's bar. The {@code max_damage} component is a 1.20.5+ item API
 * ({@code Damageable#setMaxDamage}), name-probed; below it a head cannot carry a durability bar at all, so the
 * copy degrades away whole rather than writing a damage value the client has no denominator for.
 */
public final class ModernHeadAttributes implements HeadAttributes {

    private static final Logger LOG = System.getLogger("StarEnchants.HeadAttributes");

    /** {@code Material#getDefaultAttributeModifiers(EquipmentSlot)} — absent on the 1.17.1 compile floor. */
    private static final MethodHandle DEFAULT_MODIFIERS = probeDefaults();

    /** {@code Damageable#setMaxDamage/hasMaxDamage/getMaxDamage} — the 1.20.5+ max_damage component trio. */
    private static final MethodHandle SET_MAX_DAMAGE =
            probeDamageable("setMaxDamage", MethodType.methodType(void.class, Integer.class));
    private static final MethodHandle HAS_MAX_DAMAGE =
            probeDamageable("hasMaxDamage", MethodType.methodType(boolean.class));
    private static final MethodHandle GET_MAX_DAMAGE =
            probeDamageable("getMaxDamage", MethodType.methodType(int.class));

    @Override
    @SuppressWarnings("unchecked")
    public void copyWorn(ItemStack shownHead, ItemStack realHelmet) {
        ItemMeta headMeta = shownHead.getItemMeta();
        ItemMeta helmetMeta = realHelmet.getItemMeta();
        if (headMeta == null) {
            return;
        }
        try {
            if (helmetMeta != null && helmetMeta.hasAttributeModifiers()) {
                // Explicit stats (heroic vanilla-stats et al.) — the tooltip's source of truth once any exist.
                for (Map.Entry<Attribute, AttributeModifier> entry : helmetMeta.getAttributeModifiers().entries()) {
                    headMeta.addAttributeModifier(entry.getKey(), entry.getValue());
                }
            } else if (DEFAULT_MODIFIERS != null) {
                // No explicit stats: the client would have shown the material's vanilla defaults — replicate them.
                Multimap<Attribute, AttributeModifier> defaults =
                        (Multimap<Attribute, AttributeModifier>) DEFAULT_MODIFIERS
                                .invoke(realHelmet.getType(), EquipmentSlot.HEAD);
                for (Map.Entry<Attribute, AttributeModifier> entry : defaults.entries()) {
                    headMeta.addAttributeModifier(entry.getKey(), entry.getValue());
                }
            }
            copyDurability(headMeta, realHelmet, helmetMeta);
            shownHead.setItemMeta(headMeta);
        } catch (Throwable failure) {
            // Cosmetic-only: a per-version modifier quirk must never break the repaint itself.
            LOG.log(Level.DEBUG, "worn-attribute copy skipped", failure);
        }
    }

    /**
     * Mirror the helmet's durability bar onto the shown head: its own {@code max_damage} component when one is
     * set (heroic's real max-durability, ADR-0031), else the material's vanilla max, plus the current damage.
     * Requires the 1.20.5+ component API — without it the head has no max_damage to divide by, so copy nothing.
     */
    private static void copyDurability(ItemMeta headMeta, ItemStack realHelmet, ItemMeta helmetMeta)
            throws Throwable {
        if (SET_MAX_DAMAGE == null || !(headMeta instanceof Damageable shownDamage)
                || !(helmetMeta instanceof Damageable helmetDamage)) {
            return;
        }
        int max = HAS_MAX_DAMAGE != null && (boolean) HAS_MAX_DAMAGE.invoke(helmetDamage)
                ? (int) GET_MAX_DAMAGE.invoke(helmetDamage)
                : realHelmet.getType().getMaxDurability();
        if (max <= 0) {
            return; // an undamageable helmet shows no bar — leave the head bare too
        }
        SET_MAX_DAMAGE.invoke(shownDamage, Integer.valueOf(max));
        shownDamage.setDamage(helmetDamage.getDamage());
    }

    private static MethodHandle probeDefaults() {
        try {
            return MethodHandles.publicLookup().findVirtual(Material.class, "getDefaultAttributeModifiers",
                    MethodType.methodType(Multimap.class, EquipmentSlot.class));
        } catch (NoSuchMethodException | IllegalAccessException absent) {
            LOG.log(Level.DEBUG, "Material.getDefaultAttributeModifiers absent (1.17.1) — default lines degrade");
            return null;
        }
    }

    private static MethodHandle probeDamageable(String name, MethodType type) {
        try {
            return MethodHandles.publicLookup().findVirtual(Damageable.class, name, type);
        } catch (NoSuchMethodException | IllegalAccessException absent) {
            LOG.log(Level.DEBUG, "Damageable." + name + " absent (pre-1.20.5) — durability mirror degrades");
            return null;
        }
    }
}
