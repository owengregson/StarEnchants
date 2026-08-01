package feature.combat;

import engine.effect.kind.EnchantLevels;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import feature.soul.SoulService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

/** Exact, bug-corrected native implementation of Necromancer's mastery Soul Siphon. */
public final class SoulSiphonListener implements Listener {

    private static final String ENCHANT = "enchants/soul-siphon";
    private final SinkFactory sinks;
    private final SinkEnv env;
    private final SoulService souls;

    public SoulSiphonListener(SinkFactory sinks, SinkEnv env, SoulService souls) {
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        this.souls = Objects.requireNonNull(souls, "souls");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        Player attacker = MarkOfTheBeastListener.resolvePlayerForCosmic(event.getDamager());
        if (!(event.getEntity() instanceof Player target)
                || attacker == null
                || !CosmicTierGate.tierSixPlusEnabled(attacker)
                || attacker.equals(target)
                || CombatDispatch.friendly(attacker, target)) {
            return;
        }
        int level = EnchantLevels.worn(attacker, ENCHANT);
        if (level <= 0 || level > 4) {
            return;
        }
        CosmicMasteryRouting.Route route = CosmicMasteryRouting.route(attacker, target, level);
        if (route.blocked() || ThreadLocalRandom.current().nextDouble() >= 0.04 + 0.01 * level) {
            return;
        }

        Player source = route.source();
        Player routedTarget = route.target();
        SinkReadback sink = sinks.create(env);
        sink.heal(source, 2.0 + level * 2.0);
        int targetSouls = souls.carriedTotal(routedTarget);
        int toSteal = Math.min(Math.max(1, targetSouls), (int) Math.ceil(level * 25.0));
        if (targetSouls >= toSteal) {
            SoulService.SiphonResult result = souls.siphon(routedTarget, source, toSteal);
            sink.message(routedTarget, "&c&l* SOUL SIPHON [&7-" + result.stolen()
                    + " Souls (&7" + source.getName() + ")&c&l]");
            sink.message(source, "&a&l* SOUL SIPHON [&7" + routedTarget.getName()
                    + " (+" + result.credited() + " Souls)&a&l]");
        } else {
            siphonDurability(source, routedTarget, level, sink);
        }
        sink.flush();
    }

    @SuppressWarnings("deprecation")
    private static void siphonDurability(Player attacker, Player target, int level, SinkReadback sink) {
        ItemStack[] targetArmor = target.getInventory().getArmorContents();
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
        Collections.shuffle(order);
        for (int slot : order) {
            ItemStack piece = targetArmor[slot];
            if (piece == null || piece.getType() == Material.AIR) {
                continue;
            }
            int maximum = piece.getType().getMaxDurability();
            int currentDamage = Math.max(0, piece.getDurability());
            int taken = (int) Math.min(Math.ceil(maximum * 0.025 + level * 0.02),
                    maximum - currentDamage);
            int given = Math.max(1, taken / 3);
            piece.setDurability((short) (currentDamage + taken));
            targetArmor[slot] = piece;
            target.getInventory().setArmorContents(targetArmor);

            ItemStack[] attackerArmor = attacker.getInventory().getArmorContents();
            int repairSlot = usable(attackerArmor[slot]) ? slot : firstFallback(attackerArmor, slot);
            if (repairSlot >= 0) {
                ItemStack repaired = attackerArmor[repairSlot];
                repaired.setDurability((short) Math.max(0, repaired.getDurability() - given));
                attackerArmor[repairSlot] = repaired;
                attacker.getInventory().setArmorContents(attackerArmor);
            }
            sink.message(target, "&c&l* SOUL SIPHON [&7-" + taken + " Durability&c&l]");
            sink.message(attacker, "&a&l* SOUL SIPHON [&7+" + given + " Durability&a&l]");
            return;
        }
    }

    private static int firstFallback(ItemStack[] armor, int excluded) {
        // ArmorSlot.values() source order: helmet, chestplate, leggings, boots.
        int[] sourceOrder = {3, 2, 1, 0};
        for (int slot : sourceOrder) {
            if (slot != excluded && usable(armor[slot])) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean usable(ItemStack item) {
        return item != null && item.getType() != Material.AIR;
    }
}
