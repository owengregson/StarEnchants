package tester.suite;

import item.head.HeadAttributes;
import item.head.ModernHeadAttributes;
import java.lang.reflect.Method;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import tester.harness.Harness;

/**
 * The mask illusion's durability mirror, live (ADR-0053 §5 follow-up): a damaged helmet dressed onto a shown
 * player head must carry the helmet's {@code max_damage} + {@code damage} components, so the wearer's client
 * (and durability-display mods) read the REAL helmet's bar — and a helmet with an EXPLICIT max_damage
 * (heroic's real max-durability, ADR-0031) must win over the material default. The component API is 1.20.5+;
 * below it the mirror must degrade to a bare head (no damage without a denominator), never half-copy.
 * Pure item-meta over this version's real ItemFactory — no player needed.
 */
public final class HeadDressSuite implements Harness.Scenario {

    @Override
    public void accept(Harness h) {
        Method getMaxDamage = probeGetMaxDamage();
        if (getMaxDamage == null) {
            h.expect("headdress.degradesWholePre1205");
            h.guard("headdress.degradesWholePre1205", () -> {
                ItemStack head = dressedHead(damagedHelmet(30), null);
                Damageable meta = (Damageable) head.getItemMeta();
                if (meta != null && meta.hasDamage()) {
                    throw new IllegalStateException("pre-1.20.5 head carries damage " + meta.getDamage()
                            + " with no max_damage to divide by");
                }
            });
            return;
        }

        h.expect("headdress.materialMaxMirrored");
        h.expect("headdress.explicitMaxWins");

        h.guard("headdress.materialMaxMirrored", () -> {
            ItemStack head = dressedHead(damagedHelmet(30), null);
            assertBar(head, getMaxDamage, Material.DIAMOND_HELMET.getMaxDurability(), 30);
        });
        h.guard("headdress.explicitMaxWins", () -> {
            ItemStack head = dressedHead(damagedHelmet(30), 500);
            assertBar(head, getMaxDamage, 500, 30);
        });
    }

    /** A DIAMOND_HELMET carrying {@code damage}, optionally with an explicit max_damage component (reflective —
     *  the tester compiles on the floor API, where {@code setMaxDamage} does not exist). */
    private static ItemStack damagedHelmet(int damage) {
        ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET);
        ItemMeta meta = helmet.getItemMeta();
        ((Damageable) meta).setDamage(damage);
        helmet.setItemMeta(meta);
        return helmet;
    }

    private static ItemStack dressedHead(ItemStack helmet, Integer explicitMax) {
        if (explicitMax != null) {
            try {
                ItemMeta meta = helmet.getItemMeta();
                Damageable.class.getMethod("setMaxDamage", Integer.class).invoke(meta, explicitMax);
                helmet.setItemMeta(meta);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("setMaxDamage probe passed but invoke failed", e);
            }
        }
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        HeadAttributes dresser = new ModernHeadAttributes();
        dresser.copyWorn(head, helmet);
        return head;
    }

    private static void assertBar(ItemStack head, Method getMaxDamage, int expectedMax, int expectedDamage) {
        Damageable meta = (Damageable) head.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("shown head has no meta");
        }
        int max;
        try {
            max = (int) getMaxDamage.invoke(meta);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("getMaxDamage invoke failed", e);
        }
        if (max != expectedMax) {
            throw new IllegalStateException("expected max_damage " + expectedMax + " got " + max);
        }
        if (meta.getDamage() != expectedDamage) {
            throw new IllegalStateException("expected damage " + expectedDamage + " got " + meta.getDamage());
        }
    }

    private static Method probeGetMaxDamage() {
        try {
            return Damageable.class.getMethod("getMaxDamage");
        } catch (NoSuchMethodException pre1205) {
            return null;
        }
    }
}
