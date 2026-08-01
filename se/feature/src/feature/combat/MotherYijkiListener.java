package feature.combat;

import engine.effect.kind.ActiveSets;
import engine.effect.kind.EnchantLevels;
import engine.effect.kind.HeroicArmorPieces;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import feature.compat.Hands;
import item.view.ItemViewCache;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import platform.protect.ProtectionService;
import platform.resolve.RegistryResolvers;
import platform.sched.Scheduling;

/** Mother of Yijki's exact defensive Revenge proc; its damage multipliers remain authored in the set YAML. */
public final class MotherYijkiListener implements Listener {

    private static final String SET = "sets/mother-of-yijki";
    private static final String INFINITE_LUCK = "enchants/infinite-luck";
    private static final String POLTERGEIST = "enchants/poltergeist";
    private static final long STRIKE_WARNING_TICKS = 30L;

    private final SinkFactory sinks;
    private final SinkEnv env;
    private final PhoenixListener phoenix;
    private final ProtectionService protection;
    private final ItemViewCache views;
    private final Hands hands;
    private final int witherSpawn;
    private final int witherDeath;
    private final int witch;
    private final int explosion;

    public MotherYijkiListener(SinkFactory sinks, SinkEnv env, PhoenixListener phoenix,
                               ProtectionService protection, ItemViewCache views, Hands hands,
                               RegistryResolvers resolvers) {
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        this.phoenix = Objects.requireNonNull(phoenix, "phoenix");
        this.protection = Objects.requireNonNull(protection, "protection");
        this.views = Objects.requireNonNull(views, "views");
        this.hands = Objects.requireNonNull(hands, "hands");
        Objects.requireNonNull(resolvers, "resolvers");
        witherSpawn = resolvers.sound("WITHER_SPAWN").orElse(-1);
        witherDeath = resolvers.sound("WITHER_DEATH").orElse(-1);
        witch = resolvers.particle("SPELL_WITCH").orElse(-1);
        explosion = resolvers.particle("EXPLOSION_LARGE").orElse(-1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDefense(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player wearer) || !ActiveSets.has(wearer, SET)) {
            return;
        }
        double chance = 0.05;
        ItemStack held = hands.mainHand(wearer);
        if (held != null && held.getType() == Material.DIAMOND_SWORD
                && CosmicSetCombatListener.weapon(held, SET, views)) {
            chance *= 1.25;
        }
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        activate(wearer);
    }

    private void activate(Player caster) {
        List<Location> strikes = new ArrayList<>(16);
        SinkReadback opening = sinks.create(env);
        Location center = caster.getLocation();
        for (int i = 0; i < 16; i++) {
            Location strike = nearby(center);
            strikes.add(strike);
            if (witherSpawn >= 0) {
                opening.sound(strike, witherSpawn, 1.0f, 0.4f);
            }
            if (witch >= 0) {
                opening.particle(strike, witch, 32, -1, 0.7, 0.7, 0.7, 0.0);
                opening.particle(strike.clone().add(0.0, 1.0, 0.0), witch, 32,
                        -1, 0.7, 0.7, 0.7, 0.0);
            }
        }

        List<Player> targets = new ArrayList<>();
        for (Entity nearby : caster.getNearbyEntities(32.0, 64.0, 32.0)) {
            if (!(nearby instanceof Player target) || target.getGameMode() != GameMode.SURVIVAL
                    || CombatDispatch.friendly(caster, target)
                    || !protection.allows(caster.getUniqueId(), target.getLocation())
                    || infiniteLuck(target, caster, 4)) {
                continue;
            }
            targets.add(target);
            opening.message(target, "&5&l** REVENGE OF YIJKI (&c" + caster.getName()
                    + " [1.5s]&5&l) **");
        }
        opening.flush();

        for (Location strike : strikes) {
            Scheduling.onRegionLater(strike, STRIKE_WARNING_TICKS, () -> {
                if (strike.getWorld() == null) {
                    return;
                }
                strike.getWorld().strikeLightningEffect(strike);
                SinkReadback impact = sinks.create(env);
                if (witherDeath >= 0) {
                    impact.sound(strike, witherDeath, 1.0f, 0.4f);
                }
                if (explosion >= 0) {
                    impact.particle(strike, explosion, 4, -1, 0.7, 0.7, 0.7, 0.0);
                    impact.particle(strike.clone().add(0.0, 1.0, 0.0), explosion, 4,
                            -1, 0.7, 0.7, 0.7, 0.0);
                }
                impact.flush();
                for (Player target : targets) {
                    Scheduling.onEntity(target, () -> hit(caster, target, strike));
                }
            });
        }
    }

    private void hit(Player caster, Player target, Location strike) {
        if (!target.isOnline() || target.isDead() || !target.getWorld().equals(strike.getWorld())
                || target.getLocation().distanceSquared(strike) > 2.0
                || CombatDispatch.friendly(caster, target)
                || !protection.allows(caster.getUniqueId(), target.getLocation())
                || infiniteLuck(target, caster, 4)) {
            return;
        }
        int poltergeist = CosmicTierGate.tierSixPlusEnabled(target)
                ? EnchantLevels.worn(target, POLTERGEIST) : 0;
        if (poltergeist > 0 && ThreadLocalRandom.current().nextDouble(100.0) < poltergeist * 12.5) {
            target.sendMessage(platform.text.Colors.translate(
                    "&4&l* POLTERGEIST [&7Immune: Mother of Yijki&4&l] *"));
            return;
        }
        double damage = 16.0; // external faction HEROIC_ARMOR_SET upgrade is outside SE's boundary
        if (!phoenix.trySave(target, caster, damage)) {
            target.setHealth(Math.max(1.0, target.getHealth() - damage));
        }
    }

    private static boolean infiniteLuck(Player target, Player caster, int required) {
        if (!CosmicTierGate.tierSixPlusEnabled(target)) {
            return false;
        }
        int level = EnchantLevels.worn(target, INFINITE_LUCK);
        // Source bug used the beneficiary's heroic armor; intended lore says the enemy's heroic armor counters it.
        int heroic = HeroicArmorPieces.count(caster);
        if (level < required) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble() >= Math.min(1.0, heroic * 0.125);
    }

    private static Location nearby(Location center) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int dx = (random.nextBoolean() ? 1 : -1) * (random.nextInt(8) + 2);
        int dz = (random.nextBoolean() ? 1 : -1) * (random.nextInt(8) + 2);
        Location candidate = center.clone().add(dx + 0.5, 0.0, dz + 0.5);
        Location highest = candidate.getWorld().getHighestBlockAt(candidate).getLocation();
        return highest.getY() < candidate.getY() ? highest : candidate;
    }
}
