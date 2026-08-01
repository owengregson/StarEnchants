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
import org.bukkit.GameMode;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import platform.protect.ProtectionService;
import platform.resolve.RegistryResolvers;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;

/** Native, bug-corrected implementation of Cosmic Nature Wrath. */
public final class NatureWrathListener implements Listener {

    private static final String ENCHANT = "enchants/nature-wrath";
    private static final String ZEUS_MASK = "masks/zeus-mask";

    private final SinkFactory sinks;
    private final SinkEnv env;
    private final SoulService souls;
    private final ProtectionService protection;
    private final Map<UUID, Wrath> active = new ConcurrentHashMap<>();
    private final int jump;
    private final int slow;
    private final int weakness;
    private final int levelUp;
    private final int itemBreak;
    private final int dragonGrowl;
    private final int ghastScream;
    private final int lava;
    private final int largeExplosion;
    private final int spell;
    private final int magicCrit;
    private final int digSnow;
    private final int witherShoot;

    public NatureWrathListener(SinkFactory sinks, SinkEnv env, SoulService souls,
                               ProtectionService protection, RegistryResolvers resolvers) {
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        this.souls = Objects.requireNonNull(souls, "souls");
        this.protection = Objects.requireNonNull(protection, "protection");
        Objects.requireNonNull(resolvers, "resolvers");
        jump = resolvers.potionEffect("JUMP").orElse(-1);
        slow = resolvers.potionEffect("SLOW").orElse(-1);
        weakness = resolvers.potionEffect("WEAKNESS").orElse(-1);
        levelUp = resolvers.sound("LEVEL_UP").orElse(-1);
        itemBreak = resolvers.sound("ITEM_BREAK").orElse(-1);
        dragonGrowl = resolvers.sound("ENDERDRAGON_GROWL").orElse(-1);
        ghastScream = resolvers.sound("GHAST_SCREAM2").orElse(-1);
        lava = resolvers.particle("LAVA").orElse(-1);
        largeExplosion = resolvers.particle("LARGE_EXPLODE").orElse(-1);
        spell = resolvers.particle("SPELL").orElse(-1);
        magicCrit = resolvers.particle("MAGIC_CRIT").orElse(-1);
        digSnow = resolvers.sound("DIG_SNOW").orElse(-1);
        witherShoot = resolvers.sound("WITHER_SHOOT").orElse(-1);
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
        if (level <= 0 || level > 4 || ThreadLocalRandom.current().nextDouble() >= 0.004 * level) {
            return;
        }
        long now = env.nowTicks().getAsLong();
        if (env.stores().vars().get(wearer.getUniqueId(), "soul-trapped", now) != null) {
            return;
        }
        int cost = wearer.getWorld().getName().equals("world_koth") ? 150 : 75;
        if (!souls.trySpendCarried(wearer, cost)) {
            SinkReadback failed = sinks.create(env);
            if (lava >= 0) {
                failed.particle(wearer.getEyeLocation(), lava, 20, -1, 0.4, 0.4, 0.4, 0.0);
            }
            if (itemBreak >= 0) {
                failed.privateSound(wearer, itemBreak, 0.7f, 0.4f);
            }
            failed.message(wearer, "&c&l** OUT OF SOULS **");
            failed.flush();
            return;
        }

        SinkReadback feedback = sinks.create(env);
        feedback.message(wearer, "");
        feedback.message(wearer, "&a&l** NATURE'S WRATH **");
        feedback.message(wearer, "&c&l- " + cost + " Soul Gems");
        feedback.message(wearer, "&7You have &n" + souls.currentTotal(wearer)
                + "&7 souls left.");
        if (levelUp >= 0) {
            feedback.privateSound(wearer, levelUp, 1.0f, 0.65f);
        }
        feedback.message(wearer, "");
        feedback.flush();
        activate(wearer, level, 1.0);
    }

    /** Whether Gaia can affect at least one valid enemy player before its cooldown is spent. */
    public boolean hasValidTargets(Player wearer, int level) {
        double radius = 8 + level * 5;
        for (Entity entity : wearer.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Player victim && validPlayerTarget(wearer, victim)
                    && !ActiveMasks.has(victim, ZEUS_MASK)) {
                return true;
            }
        }
        return false;
    }

    /** Free Gaia invocation of Nature's Wrath at Cosmic's exact 50% release/DoT duration. */
    public int activatePet(Player wearer, int level) {
        int targets = activate(wearer, level, 0.5);
        if (targets > 0) {
            SinkReadback sink = sinks.create(env);
            sink.message(wearer, "&a&lPET: ** NATURE'S WRATH **");
            if (digSnow >= 0) {
                sink.privateSound(wearer, digSnow, 1.1f, 3.0f);
            }
            if (witherShoot >= 0) {
                sink.privateSound(wearer, witherShoot, 1.1f, 3.0f);
            }
            sink.flush();
        }
        return targets;
    }

    private int activate(Player wearer, int level, double durationModifier) {
        double radius = 8 + level * 5;
        int potionDuration = potionDuration(level);
        int releaseDuration = releaseDuration(level,
                wearer.getWorld().getName().equals("world_koth"), durationModifier);
        int targets = 0;
        for (Entity entity : wearer.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof EnderCrystal || entity.hasMetadata("spectator")
                    || !protection.allows(wearer.getUniqueId(), entity.getLocation())) {
                continue;
            }
            if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
                if (entity.hasMetadata("do_not_clear")) {
                    continue;
                }
                SinkReadback sink = sinks.create(env);
                sink.lightning(living, false, 0.0, wearer);
                if (largeExplosion >= 0) {
                    sink.particle(living.getLocation().add(0.0, 1.0, 0.0), largeExplosion, 10,
                            -1, 0.6, 0.6, 0.6, 0.0);
                }
                if (spell >= 0) {
                    sink.particle(living.getLocation().add(0.0, 1.0, 0.0), spell, 35,
                            -1, 0.4, 0.4, 0.4, 0.0);
                }
                sink.flush();
                Scheduling.onEntity(living, living::remove);
                continue;
            }
            if (!(entity instanceof Player victim) || !validPlayerTarget(wearer, victim)) {
                continue;
            }
            if (ActiveMasks.has(victim, ZEUS_MASK)) {
                SinkReadback dodged = sinks.create(env);
                dodged.message(victim, "&d&l* ZEUS MASK [&7Natures Wrath Dodged&d&l] *");
                dodged.flush();
                continue;
            }
            beginVictim(wearer, victim, level, potionDuration, releaseDuration);
            targets++;
        }
        return targets;
    }

    static int potionDuration(int level) {
        return (7 + level) * 20;
    }

    static int releaseDuration(int level, boolean koth, double durationModifier) {
        int base = (7 + level) * (koth ? 5 : 20);
        return Math.max(1, (int) (base * durationModifier));
    }

    private boolean validPlayerTarget(Player wearer, Player victim) {
        return !victim.equals(wearer)
                && !CombatDispatch.friendly(wearer, victim)
                && !active.containsKey(victim.getUniqueId())
                && victim.getGameMode() == GameMode.SURVIVAL
                && !victim.hasMetadata("spectator")
                && !victim.hasMetadata("god") && !victim.hasMetadata("godmode")
                && protection.allows(wearer.getUniqueId(), victim.getLocation());
    }

    private void beginVictim(Player wearer, Player victim, int level, int potionDuration, int releaseDuration) {
        // Intended Poltergeist contract: 12.5% per level. The source accidentally checked only > 0.
        int poltergeist = CosmicTierGate.tierSixPlusEnabled(victim)
                ? EnchantLevels.worn(victim, "enchants/poltergeist") : 0;
        boolean immune = poltergeist > 0
                && ThreadLocalRandom.current().nextDouble() * 100.0 < 12.5 * poltergeist;
        SinkReadback sink = sinks.create(env);
        sink.lightning(victim, false, 0.0, wearer);
        if (immune) {
            sink.message(victim, "&4&l* POLTERGEIST [&7Immune: Nature's Wrath&4&l] *");
        } else {
            sink.movementSpeed(victim, 0.0, releaseDuration);
            if (jump >= 0) {
                sink.potionForce(victim, jump, 128, potionDuration);
            }
            if (slow >= 0) {
                sink.potionForce(victim, slow, 128, potionDuration);
            }
            if (weakness >= 0) {
                sink.potionForce(victim, weakness, 2, potionDuration);
            }
        }
        if (dragonGrowl >= 0) {
            sink.privateSound(victim, dragonGrowl, 2.0f, 2.0f);
        }
        sink.flush();

        Wrath state = new Wrath(wearer, level, !immune);
        Wrath previous = active.putIfAbsent(victim.getUniqueId(), state);
        if (previous != null) {
            return;
        }
        state.task = Scheduling.repeatingEntity(victim, 20L, 20L, () -> tick(victim, state));
        Scheduling.onEntityLater(victim, releaseDuration, () -> finish(victim, state, false));
    }

    private void tick(Player victim, Wrath expected) {
        if (active.get(victim.getUniqueId()) != expected || victim.isDead() || !victim.isOnline()) {
            finish(victim, expected, false);
            return;
        }
        SinkReadback sink = sinks.create(env);
        sink.lightning(victim, false, 0.0, expected.wearer);
        sink.damage(victim, expected.level);
        if (ghastScream >= 0) {
            sink.privateSound(victim, ghastScream, 2.0f, 2.0f);
        }
        sink.message(victim, "&2&l** NATURE'S WRATH **");
        if (spell >= 0) {
            sink.particle(victim.getLocation().add(0.0, 1.0, 0.0), spell, 35,
                    -1, 0.4, 0.4, 0.4, 0.0);
        }
        sink.flush();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker) {
            Wrath state = active.get(attacker.getUniqueId());
            if (state != null && state.frozen) {
                event.setCancelled(true);
                if (magicCrit >= 0) {
                    SinkReadback sink = sinks.create(env);
                    sink.particle(event.getEntity().getLocation(), magicCrit, 30,
                            -1, 0.6, 0.6, 0.6, 0.0);
                    sink.flush();
                }
            }
        }
        if (event.getEntity() instanceof Player victim) {
            Wrath state = active.get(victim.getUniqueId());
            if (state != null && ThreadLocalRandom.current().nextDouble()
                    < 0.3 - 0.075 * state.level) {
                finish(victim, state, true);
            }
        }
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player) {
            Wrath state = active.get(player.getUniqueId());
            if (state != null && state.frozen) {
                event.setCancelled(true);
                if (magicCrit >= 0) {
                    SinkReadback sink = sinks.create(env);
                    sink.particle(player.getEyeLocation(), magicCrit, 30,
                            -1, 0.6, 0.6, 0.6, 0.0);
                    sink.flush();
                }
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Wrath state = active.get(event.getPlayer().getUniqueId());
        if (state != null) {
            finish(event.getPlayer(), state, true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Wrath state = active.get(event.getPlayer().getUniqueId());
        if (state != null) {
            finish(event.getPlayer(), state, true);
        }
    }

    public void stop() {
        for (Map.Entry<UUID, Wrath> entry : java.util.List.copyOf(active.entrySet())) {
            Player victim = org.bukkit.Bukkit.getPlayer(entry.getKey());
            if (victim != null) {
                finish(victim, entry.getValue(), true);
            } else if (active.remove(entry.getKey(), entry.getValue())
                    && entry.getValue().task != null) {
                entry.getValue().task.cancel();
            }
        }
        active.clear();
    }

    private void finish(Player victim, Wrath expected, boolean clearRootEffects) {
        if (!active.remove(victim.getUniqueId(), expected)) {
            return;
        }
        if (expected.task != null) {
            expected.task.cancel();
        }
        if (expected.frozen) {
            victim.setWalkSpeed(0.2f);
            if (clearRootEffects) {
                SinkReadback clear = sinks.create(env);
                if (jump >= 0) {
                    clear.removePotion(victim, jump);
                }
                if (slow >= 0) {
                    clear.removePotion(victim, slow);
                }
                clear.flush();
            }
        }
        if (clearRootEffects && largeExplosion >= 0) {
            SinkReadback sink = sinks.create(env);
            sink.particle(victim.getLocation(), largeExplosion, 4, -1, 0.8, 0.8, 0.8, 0.0);
            sink.flush();
        }
    }

    private static final class Wrath {
        final Player wearer;
        final int level;
        final boolean frozen;
        volatile TaskHandle task;

        Wrath(Player wearer, int level, boolean frozen) {
            this.wearer = wearer;
            this.level = level;
            this.frozen = frozen;
        }
    }
}
