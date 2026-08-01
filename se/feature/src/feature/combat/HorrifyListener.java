package feature.combat;

import engine.effect.kind.ActiveSets;
import engine.effect.kind.EnchantLevels;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import platform.protect.ProtectionService;
import platform.resolve.RegistryResolvers;
import platform.sched.Scheduling;

/** Exact intended implementation of Cosmic's Ghost-set mastery enchant Horrify. */
public final class HorrifyListener implements Listener {

    private static final String ENCHANT = "enchants/horrify";
    private static final String DRAGON_SLAYER = "sets/dragon-slayer";
    private static final double RANGE = 32.0;
    private static final long FALL_GUARD_TICKS = 200L;

    private final SinkFactory sinks;
    private final SinkEnv env;
    private final ProtectionService protection;
    private final LongSupplier nowTicks;
    private final int jump;
    private final int scream;
    private final Map<UUID, Long> horrifiedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> fallGuardUntil = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> pendingFriendlyUncancel = ConcurrentHashMap.newKeySet();

    public HorrifyListener(SinkFactory sinks, SinkEnv env, ProtectionService protection,
                           RegistryResolvers resolvers, LongSupplier nowTicks) {
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        this.protection = Objects.requireNonNull(protection, "protection");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        Objects.requireNonNull(resolvers, "resolvers");
        this.jump = resolvers.potionEffect("JUMP").orElse(-1);
        this.scream = resolvers.sound("GHAST_SCREAM").orElse(-1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDefense(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player wearer)
                || !CosmicTierGate.tierSixPlusEnabled(wearer)
                || !CosmicDefenseGate.sourceCombatCause(event.getCause())
                || CosmicDefenseGate.silenced(wearer, env)) {
            return;
        }
        int level = EnchantLevels.worn(wearer, ENCHANT);
        if (level <= 0 || level > 4 || ThreadLocalRandom.current().nextDouble() >= level * 0.02) {
            return;
        }

        int seconds = 2 + level / 2; // exact integer source scaling: 2, 3, 3, 4.
        int duration = seconds * 20;
        int affected = 0;
        SinkReadback sink = sinks.create(env);
        for (Entity nearby : wearer.getNearbyEntities(RANGE, RANGE, RANGE)) {
            if (!(nearby instanceof Player target) || target.equals(wearer)
                    || CombatDispatch.friendly(wearer, target)
                    || !protection.allows(wearer.getUniqueId(), target.getLocation())
                    || !wearer.canSee(target)
                    || target.hasMetadata("spectator")
                    || target.hasMetadata("god") || target.hasMetadata("godmode")
                    || target.getGameMode() == GameMode.CREATIVE) {
                continue;
            }
            if (ActiveSets.has(target, DRAGON_SLAYER)) {
                sink.message(target, "&8&l* DRAGON SLAYER [&7Horrify blocked!&8&l] *");
                continue; // the source returned from the whole loop accidentally; immunity is per target.
            }

            sink.message(target, "&c&l* HORRIFIED &c[&c&l" + seconds + "s&c] &c&l*");
            if (scream >= 0) {
                sink.privateSound(target, scream, 1.0f, 1.4f);
            }
            sink.movementSpeed(target, 0.001, duration);
            if (jump >= 0) {
                sink.potionForce(target, jump, 128, duration);
            }
            sink.faceAway(target, wearer.getEyeLocation());
            long now = nowTicks.getAsLong();
            horrifiedUntil.put(target.getUniqueId(), now + duration);
            fallGuardUntil.put(target.getUniqueId(), now + FALL_GUARD_TICKS);
            Scheduling.onEntityLater(target, duration,
                    () -> horrifiedUntil.remove(target.getUniqueId(), now + duration));
            affected++;
        }
        if (affected > 0) {
            sink.message(wearer, "&a&l* HORRIFIED " + affected + " player(s) [&7"
                    + seconds + "s&a&l] *");
        }
        sink.flush();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onFriendlyHitLow(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof Player victim)
                || !horrified(attacker)
                || !CombatDispatch.friendly(attacker, victim)
                || !protection.allows(attacker.getUniqueId(), victim.getLocation())) {
            return;
        }
        event.setCancelled(true);
        pendingFriendlyUncancel.add(attacker.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFriendlyHitHighest(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        boolean pending = pendingFriendlyUncancel.remove(attacker.getUniqueId());
        if (!event.isCancelled()
                || !pending
                || event.getDamage() <= 0.0
                || !protection.allows(attacker.getUniqueId(), attacker.getLocation())
                || !protection.allows(attacker.getUniqueId(), victim.getLocation())) {
            return;
        }
        event.setCancelled(false);
    }

    private boolean horrified(Player player) {
        long now = nowTicks.getAsLong();
        Long until = horrifiedUntil.get(player.getUniqueId());
        if (until == null || until <= now) {
            if (until != null) {
                horrifiedUntil.remove(player.getUniqueId(), until);
            }
            return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player player)) {
            return;
        }
        Long until = fallGuardUntil.remove(player.getUniqueId());
        if (until != null && until > nowTicks.getAsLong()) {
            event.setCancelled(true);
            event.setDamage(0.0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        horrifiedUntil.remove(id);
        fallGuardUntil.remove(id);
        pendingFriendlyUncancel.remove(id);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        horrifiedUntil.remove(id);
        fallGuardUntil.remove(id);
        pendingFriendlyUncancel.remove(id);
    }

    public void stop() {
        for (UUID id : java.util.List.copyOf(horrifiedUntil.keySet())) {
            Player player = org.bukkit.Bukkit.getPlayer(id);
            if (player != null) {
                player.setWalkSpeed(0.2f);
            }
        }
        horrifiedUntil.clear();
        fallGuardUntil.clear();
        pendingFriendlyUncancel.clear();
    }
}
