package feature.combat;

import engine.effect.kind.ActiveSets;
import engine.effect.kind.HeroicArmorPieces;
import feature.compat.Hands;
import item.view.ItemViewCache;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

/** Source-faithful set combat branches that need held-item/type and per-slot durability inspection. */
public final class CosmicSetCombatListener implements Listener {

    private static final String YETI = "sets/yeti";
    private static final String KOTH = "sets/koth";

    private final ItemViewCache views;
    private final Hands hands;

    public CosmicSetCombatListener(ItemViewCache views, Hands hands) {
        this.views = Objects.requireNonNull(views, "views");
        this.hands = Objects.requireNonNull(hands, "hands");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return; // neither set advertises/supports bow damage in the source dispatcher
        }
        if (ActiveSets.has(attacker, YETI)) {
            yeti(attacker, event);
        } else if (ActiveSets.has(attacker, KOTH)) {
            koth(attacker, event);
        }
    }

    private void yeti(Player attacker, EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        ItemStack held = hands.mainHand(attacker);
        if (!weapon(held, YETI, views) || held.getType() != Material.DIAMOND_AXE) {
            return;
        }
        damageArmorChance(victim, 0.75, 1, 1);
        // Intended form of the source formula: +7.5%, then +3% per worn Heroic armor piece.
        // The decompiled extra "+ 1.0" was the documented accidental double-base bug.
        event.setDamage(event.getDamage() * (1.075 + HeroicArmorPieces.count(victim) * 0.03));
    }

    private void koth(Player attacker, EntityDamageByEntityEvent event) {
        event.setDamage(event.getDamage() * (event.getEntity() instanceof Player ? 1.2 : 1.5));
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        ItemStack held = hands.mainHand(attacker);
        if (!weapon(held, KOTH, views)) {
            return;
        }
        if (gold(held, "AXE")) {
            damageArmorChance(victim, 0.5, 2, 3);
        } else if (gold(held, "SWORD")) {
            event.setDamage(event.getDamage() * 1.075);
        }
    }

    static boolean weapon(ItemStack held, String set, ItemViewCache views) {
        return held != null && held.getType() != Material.AIR
                && set.equals(views.of(held).combat().setWeaponKey());
    }

    private static boolean gold(ItemStack held, String tool) {
        String name = held.getType().name();
        return name.equals("GOLD_" + tool) || name.equals("GOLDEN_" + tool);
    }

    @SuppressWarnings("deprecation")
    private static void damageArmorChance(Player victim, double chance, int min, int max) {
        ItemStack[] armor = victim.getInventory().getArmorContents();
        boolean changed = false;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int slot = 0; slot < armor.length; slot++) {
            ItemStack piece = armor[slot];
            if (piece == null || piece.getType() == Material.AIR || random.nextDouble() > chance) {
                continue;
            }
            int amount = min == max ? min : random.nextInt(min, max + 1);
            int next = piece.getDurability() + amount;
            if (piece.getType().getMaxDurability() > 0 && next >= piece.getType().getMaxDurability()) {
                armor[slot] = null;
            } else {
                piece.setDurability((short) next);
            }
            changed = true;
        }
        if (changed) {
            victim.getInventory().setArmorContents(armor);
        }
    }
}
