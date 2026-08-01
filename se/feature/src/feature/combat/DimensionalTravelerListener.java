package feature.combat;

import engine.effect.kind.ActiveSets;
import engine.effect.kind.EnchantLevels;
import engine.effect.kind.HeroicArmorPieces;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import platform.protect.ProtectionService;
import platform.resolve.RegistryResolvers;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;

/** Dimensional Traveler's 1% shift, freeze, and damaging falling-block storm. */
public final class DimensionalTravelerListener implements Listener {

    private static final String SET = "sets/dimensional-traveler";
    private static final String INFINITE_LUCK = "enchants/infinite-luck";
    private static final String POLTERGEIST = "enchants/poltergeist";
    private static final String CLARITY = "enchants/clarity";

    private record HitWindow(long start, int hits) {
    }

    private record Cast(FallingBlock block, UUID owner, int materialId, long born, TaskHandle task) {
    }

    private final SinkFactory sinks;
    private final SinkEnv env;
    private final PhoenixListener phoenix;
    private final ProtectionService protection;
    private final Map<UUID, Float> storedWalkSpeed = new ConcurrentHashMap<>();
    private final Map<UUID, Long> freezeExpiry = new ConcurrentHashMap<>();
    private final Map<UUID, Long> ignoreUntil = new ConcurrentHashMap<>();
    private final Map<UUID, HitWindow> hitWindows = new ConcurrentHashMap<>();
    private final Map<UUID, Cast> casts = new ConcurrentHashMap<>();
    private final int blindness;
    private final int slow;
    private final int endermanTeleport;
    private final int anvilLand;
    private final int zombieWoodBreak;
    private final int endStoneId;
    private final int netherrackId;
    private final Material endStone;
    private final Material netherrack;

    public DimensionalTravelerListener(SinkFactory sinks, SinkEnv env, PhoenixListener phoenix,
                                       ProtectionService protection, RegistryResolvers resolvers) {
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        this.phoenix = Objects.requireNonNull(phoenix, "phoenix");
        this.protection = Objects.requireNonNull(protection, "protection");
        Objects.requireNonNull(resolvers, "resolvers");
        blindness = resolvers.potionEffect("BLINDNESS").orElse(-1);
        slow = resolvers.potionEffect("SLOW").orElse(-1);
        endermanTeleport = resolvers.sound("ENDERMAN_TELEPORT").orElse(-1);
        anvilLand = resolvers.sound("ANVIL_LAND").orElse(-1);
        zombieWoodBreak = resolvers.sound("ZOMBIE_WOODBREAK").orElse(-1);
        endStoneId = resolvers.material("ENDER_STONE").orElse(-1);
        netherrackId = resolvers.material("NETHERRACK").orElse(-1);
        endStone = Material.matchMaterial("ENDER_STONE");
        netherrack = Material.matchMaterial("NETHERRACK");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDefense(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player wearer) || !ActiveSets.has(wearer, SET)
                || wearer.getWorld().getName().contains("dungeon")
                || ThreadLocalRandom.current().nextDouble() >= 0.01) {
            return;
        }
        shift(wearer);
    }

    private void shift(Player caster) {
        long now = env.nowTicks().getAsLong();
        for (Entity nearby : caster.getNearbyEntities(25.0, 32.0, 25.0)) {
            if (!(nearby instanceof Player target) || target.getGameMode() != GameMode.SURVIVAL
                    || target.isDead() || CombatDispatch.friendly(caster, target)
                    || !protection.allows(caster.getUniqueId(), target.getLocation())
                    || infiniteLuck(target, caster, 5)) {
                continue;
            }
            int poltergeist = CosmicTierGate.tierSixPlusEnabled(target)
                    ? EnchantLevels.worn(target, POLTERGEIST) : 0;
            if (poltergeist > 0
                    && ThreadLocalRandom.current().nextInt(100) + 1 <= poltergeist * 12.5) {
                ignoreUntil.put(target.getUniqueId(), now + 160L);
                target.sendMessage(platform.text.Colors.translate(
                        "&4&l* POLTERGEIST [&7Immune: Dimensional Traveler&4&l] *"));
                continue;
            }
            freeze(caster, target, now);
            spawnStorm(caster, target);
        }
    }

    private void freeze(Player caster, Player target, long now) {
        UUID id = target.getUniqueId();
        if (target.getWalkSpeed() > 0.0f) {
            storedWalkSpeed.putIfAbsent(id, target.getWalkSpeed());
            target.setWalkSpeed(0.0f);
        }
        freezeExpiry.merge(id, now + 80L, Math::max);
        SinkReadback sink = sinks.create(env);
        if (blindness >= 0 && EnchantLevels.worn(target, CLARITY) <= 0) {
            sink.potionForce(target, blindness, 0, 60);
        }
        if (slow >= 0) {
            sink.potionForce(target, slow, 0, 80);
        }
        Location above = target.getLocation().clone().add(0.0, 4.0, 0.0);
        if (endermanTeleport >= 0) {
            sink.privateSoundAt(target, above, endermanTeleport, 1.0f, 1.1f);
        }
        if (anvilLand >= 0) {
            sink.privateSoundAt(target, above, anvilLand, 1.0f, 1.1f);
        }
        sink.message(target, "&5&l** DIMENSIONAL SHIFT (&c" + caster.getName() + " [4s]&5&l) **");
        sink.flush();
        Scheduling.onEntityLater(target, 80L, () -> {
            Long expiry = freezeExpiry.get(id);
            if (expiry != null && env.nowTicks().getAsLong() >= expiry) {
                unfreeze(target);
            }
        });
    }

    private void spawnStorm(Player caster, Player target) {
        if (endStone == null || netherrack == null) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int layers = 3 + random.nextInt(2);
        Location base = target.getLocation().clone().add(0.0, 10.0, 0.0);
        int y = base.getBlockY();
        for (int layer = 0; layer < layers; layer++) {
            y += (12 + random.nextInt(8)) * layer;
            if (y <= 0 || y >= Math.min(255, base.getWorld().getMaxHeight())) {
                continue;
            }
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    if (random.nextBoolean()) {
                        continue;
                    }
                    Material material = random.nextBoolean() ? endStone : netherrack;
                    int materialId = material == endStone ? endStoneId : netherrackId;
                    Location at = new Location(base.getWorld(), base.getBlockX() + x, y, base.getBlockZ() + z);
                    Scheduling.onRegion(at, () -> spawnCast(at, material, materialId, caster));
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void spawnCast(Location at, Material material, int materialId, Player caster) {
        FallingBlock block = at.getWorld().spawnFallingBlock(at, material, (byte) 0);
        block.setDropItem(false);
        block.setHurtEntities(false);
        UUID id = block.getUniqueId();
        long born = env.nowTicks().getAsLong();
        casts.put(id, new Cast(block, caster.getUniqueId(), materialId, born, TaskHandle.CANCELLED));
        TaskHandle task = Scheduling.repeatingEntity(block, 1L, 1L, () -> tickCast(id));
        casts.computeIfPresent(id, (key, cast) -> new Cast(cast.block(), cast.owner(), cast.materialId(), cast.born(), task));
    }

    private void tickCast(UUID id) {
        Cast cast = casts.get(id);
        if (cast == null) {
            return;
        }
        FallingBlock block = cast.block();
        long now = env.nowTicks().getAsLong();
        if (!block.isValid() || block.isDead() || block.isOnGround() || now - cast.born() > 300L) {
            removeCast(id, false);
            return;
        }
        if ((now - cast.born()) % 20L == 0L) {
            String here = block.getLocation().getBlock().getType().name();
            String below = block.getLocation().clone().subtract(0.0, 1.0, 0.0).getBlock().getType().name();
            if (here.equals("WEB") || here.equals("COBWEB") || below.equals("WEB") || below.equals("COBWEB")) {
                removeCast(id, false);
                return;
            }
        }
        Player owner = org.bukkit.Bukkit.getPlayer(cast.owner());
        if (owner == null) {
            removeCast(id, false);
            return;
        }
        for (Entity entity : block.getNearbyEntities(0.75, 0.75, 0.75)) {
            if (entity instanceof Player target && damageable(owner, target, now) && recordHit(target, now)) {
                double damage = Math.min(target.getMaxHealth(), 44.0) * 0.15;
                if (!phoenix.trySave(target, owner, damage)) {
                    target.setHealth(Math.max(0.0, target.getHealth() - damage));
                }
                unfreeze(target);
                SinkReadback sink = sinks.create(env);
                if (zombieWoodBreak >= 0) {
                    sink.privateSound(target, zombieWoodBreak, 1.0f, 1.1f);
                }
                if (cast.materialId() >= 0) {
                    sink.blockBreakEffect(block.getLocation(), cast.materialId());
                }
                sink.flush();
                removeCast(id, true);
                return;
            }
        }
    }

    private boolean damageable(Player owner, Player target, long now) {
        Long ignored = ignoreUntil.get(target.getUniqueId());
        return !owner.equals(target) && target.getGameMode() != GameMode.CREATIVE
                && !target.isDead() && target.getHealth() > 0.0
                && (ignored == null || now >= ignored)
                && !CombatDispatch.friendly(owner, target)
                && protection.allows(owner.getUniqueId(), target.getLocation())
                && !infiniteLuck(target, owner, 5);
    }

    private boolean recordHit(Player target, long now) {
        UUID id = target.getUniqueId();
        final boolean[] allowed = {false};
        hitWindows.compute(id, (key, old) -> {
            HitWindow live = old == null || now - old.start() > 200L ? new HitWindow(now, 0) : old;
            if (live.hits() >= 4) {
                return live;
            }
            allowed[0] = true;
            return new HitWindow(live.start(), live.hits() + 1);
        });
        return allowed[0];
    }

    private void removeCast(UUID id, boolean alreadyEffected) {
        Cast removed = casts.remove(id);
        if (removed == null) {
            return;
        }
        removed.task().cancel();
        if (removed.block().isValid()) {
            removed.block().remove();
        }
    }

    private void unfreeze(Player player) {
        UUID id = player.getUniqueId();
        Float old = storedWalkSpeed.remove(id);
        freezeExpiry.remove(id);
        if (old != null) {
            player.setWalkSpeed(old <= 0.0f ? 0.2f : old);
        }
    }

    private static boolean infiniteLuck(Player target, Player caster, int required) {
        if (!CosmicTierGate.tierSixPlusEnabled(target)) {
            return false;
        }
        int level = EnchantLevels.worn(target, INFINITE_LUCK);
        if (level < required) {
            return false;
        }
        double counter = Math.min(1.0, HeroicArmorPieces.count(caster) * 0.125);
        return ThreadLocalRandom.current().nextDouble() >= counter;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLand(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock block && casts.containsKey(block.getUniqueId())) {
            event.setCancelled(true);
            removeCast(block.getUniqueId(), false);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        unfreeze(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        unfreeze(event.getPlayer());
        hitWindows.remove(id);
        ignoreUntil.remove(id);
    }

    public void stop() {
        for (UUID id : java.util.List.copyOf(casts.keySet())) {
            removeCast(id, false);
        }
        storedWalkSpeed.clear();
        freezeExpiry.clear();
        ignoreUntil.clear();
        hitWindows.clear();
    }
}
