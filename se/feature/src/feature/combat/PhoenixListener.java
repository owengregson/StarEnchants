package feature.combat;

import engine.effect.kind.ActiveMasks;
import engine.effect.kind.EnchantLevels;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import feature.soul.SoulService;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import platform.resolve.RegistryResolvers;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;

/** Exact, intended implementation of Cosmic Phoenix, shared with direct-health set attacks. */
public final class PhoenixListener implements Listener {

    private static final String PHOENIX = "enchants/phoenix";
    private static final String DEATH_KNIGHT_MASK = "masks/death-knight";
    private static final int PROC_DECAY_TICKS = 12_000;

    private final SinkFactory sinks;
    private final SinkEnv env;
    private final SoulService souls;
    private final Map<UUID, Long> lastProc = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> procCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> outOfSoulsMessage = new ConcurrentHashMap<>();
    private final int dragonGrowl;
    private final int itemBreak;
    private final int flame;
    private final int lava;
    private volatile TaskHandle decayTask = TaskHandle.CANCELLED;

    public PhoenixListener(SinkFactory sinks, SinkEnv env, SoulService souls, RegistryResolvers resolvers) {
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        this.souls = Objects.requireNonNull(souls, "souls");
        Objects.requireNonNull(resolvers, "resolvers");
        dragonGrowl = resolvers.sound("ENDERDRAGON_GROWL").orElse(-1);
        itemBreak = resolvers.sound("ITEM_BREAK").orElse(-1);
        flame = resolvers.particle("FLAME").orElse(-1);
        lava = resolvers.particle("LAVA").orElse(-1);
    }

    public void start() {
        decayTask = Scheduling.repeatingGlobal(PROC_DECAY_TICKS, PROC_DECAY_TICKS,
                () -> procCounts.replaceAll((id, count) -> Math.max(0, count - 1)));
    }

    public void stop() {
        decayTask.cancel();
        lastProc.clear();
        procCounts.clear();
        outOfSoulsMessage.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        int level = EnchantLevels.worn(player, PHOENIX);
        if (level <= 0) {
            return;
        }
        LivingEntity damager = null;
        if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntity) {
            Player resolved = MarkOfTheBeastListener.resolvePlayerForCosmic(byEntity.getDamager());
            damager = resolved;
        }
        double adjusted = event.getDamage() * armorNullification(player);
        if (trySave(player, damager, level, adjusted)) {
            event.setDamage(0.0);
            event.setCancelled(true);
        }
    }

    /** Phoenix-aware direct-health save used by Cosmic set attacks. Caller must run on the victim's region. */
    public boolean trySave(Player player, LivingEntity damager, double damage) {
        int level = EnchantLevels.worn(player, PHOENIX);
        return level > 0 && trySave(player, damager, level, damage);
    }

    private boolean trySave(Player player, LivingEntity damager, int level, double damage) {
        if (!CosmicTierGate.tierSixPlusEnabled(player)
                || player.getHealth() <= 0.0 || player.getHealth() - damage > 0.0) {
            return false;
        }
        long now = env.nowTicks().getAsLong();
        if (damager instanceof Player attacker && ActiveMasks.has(attacker, DEATH_KNIGHT_MASK)
                && ThreadLocalRandom.current().nextDouble() <= 0.5) {
            // Intended bug fix: a blocked save does not consume Phoenix's minutes-long cooldown.
            SinkReadback sink = sinks.create(env);
            sink.message(player, "&c&l* PHOENIX BLOCKED [&7" + attacker.getName() + "&c&l] *");
            sink.message(attacker, "&c&l* DEATH KNIGHT MASK [&7" + player.getName()
                    + "'s Phoenix Blocked&c&l] *");
            sink.flush();
            return false;
        }
        long cooldown = (long) (4 - Math.min(3, level)) * 1_200L;
        Long last = lastProc.get(player.getUniqueId());
        if (last != null && now - last <= cooldown) {
            return false;
        }
        int previous = procCounts.getOrDefault(player.getUniqueId(), 0);
        int cost = 500;
        for (int i = 0; i < previous && cost < 8_000; i++) {
            cost = Math.min(8_000, cost * 2);
        }
        if (!souls.costFree(player) && souls.carriedTotal(player) < cost) {
            outOfSouls(player, now);
            return false;
        }
        if (souls.drainUpTo(player, cost) < cost) {
            outOfSouls(player, now);
            return false;
        }

        lastProc.put(player.getUniqueId(), now);
        procCounts.put(player.getUniqueId(), previous + 1);
        player.setHealth(player.getMaxHealth());
        SinkReadback sink = sinks.create(env);
        sink.message(player, "");
        sink.message(player, "&6&l*** &nPHOENIX SOUL&6&l ***");
        sink.message(player, "&c&l- " + cost + " Soul Gems");
        sink.message(player, "&7You have &n" + souls.carriedTotal(player) + "&7 souls left.");
        sink.message(player, "");
        if (dragonGrowl >= 0) {
            sink.privateSound(player, dragonGrowl, 1.0f, 1.25f);
        }
        for (Entity nearby : player.getNearbyEntities(48.0, 48.0, 48.0)) {
            if (nearby instanceof Player viewer) {
                sink.message(viewer, "&c&l*** PHOENIX SOUL (&7" + player.getName()
                        + ", -" + cost + " souls&c&l) ***");
                if (dragonGrowl >= 0) {
                    sink.privateSoundAt(viewer, player.getLocation(), dragonGrowl, 1.0f, 1.25f);
                }
            }
        }
        if (flame >= 0) {
            sink.particle(player, flame, 80, -1, Math.random(), Math.random(), Math.random(), 0.75, 1, 0.0);
        }
        if (lava >= 0) {
            sink.particle(player, lava, 20, -1, Math.random(), Math.random(), Math.random(), 0.7, 1, 0.0);
        }
        sink.flush();
        return true;
    }

    private void outOfSouls(Player player, long now) {
        Long until = outOfSoulsMessage.get(player.getUniqueId());
        if (until != null && now < until) {
            return;
        }
        outOfSoulsMessage.put(player.getUniqueId(), now + 300L);
        SinkReadback sink = sinks.create(env);
        if (lava >= 0) {
            sink.particle(player, lava, 20, -1, 0.4, 0.4, 0.4, 0.0, 2, 0.0);
        }
        if (itemBreak >= 0) {
            sink.privateSound(player, itemBreak, 0.7f, 0.4f);
        }
        sink.message(player, "&c&l** OUT OF SOULS **");
        sink.flush();
    }

    private static double armorNullification(Player player) {
        int armor = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == null || piece.getType() == Material.AIR) {
                continue;
            }
            String name = piece.getType().name();
            if (name.contains("LEATHER_")) {
                armor += name.contains("CHESTPLATE") ? 3 : name.contains("LEGGINGS") ? 2 : 1;
            } else if (name.contains("CHAINMAIL_")) {
                armor += name.contains("CHESTPLATE") ? 5 : name.contains("LEGGINGS") ? 4
                        : name.contains("HELMET") ? 2 : 1;
            } else if (name.contains("IRON_")) {
                armor += name.contains("CHESTPLATE") ? 6 : name.contains("LEGGINGS") ? 5 : 2;
            } else if (name.contains("DIAMOND_")) {
                armor += name.contains("CHESTPLATE") ? 8 : name.contains("LEGGINGS") ? 6 : 3;
            } else if (name.contains("GOLD_") || name.contains("GOLDEN_")) {
                armor += name.contains("CHESTPLATE") ? 5 : name.contains("LEGGINGS") ? 3
                        : name.contains("HELMET") ? 2 : 1;
            }
        }
        return 1.0 - armor * 0.04;
    }
}
