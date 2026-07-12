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
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The modern {@link HeadAttributes}: explicit helmet modifiers copy verbatim (heroic's real stats, ADR-0031);
 * a modifier-less vanilla helmet contributes its material's HEAD-slot defaults via
 * {@code Material#getDefaultAttributeModifiers(EquipmentSlot)} — a 1.18+ API name-probed once (Bukkit surface,
 * mapping-flip immune); on the 1.17.1 floor exactly, default lines degrade away while explicit ones still copy.
 */
public final class ModernHeadAttributes implements HeadAttributes {

    private static final Logger LOG = System.getLogger("StarEnchants.HeadAttributes");

    /** {@code Material#getDefaultAttributeModifiers(EquipmentSlot)} — absent on the 1.17.1 compile floor. */
    private static final MethodHandle DEFAULT_MODIFIERS = probeDefaults();

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
            shownHead.setItemMeta(headMeta);
        } catch (Throwable failure) {
            // Cosmetic-only: a per-version modifier quirk must never break the repaint itself.
            LOG.log(Level.DEBUG, "worn-attribute copy skipped", failure);
        }
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
}
