package feature.combat;

import engine.effect.kind.ActiveSets;
import engine.effect.kind.EnchantLevels;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import platform.protect.ProtectionService;
import platform.resolve.RegistryResolvers;
import platform.sched.Scheduling;

/** Native Death Knight Tombstone cast; landing payload remains authored through IMPACT. */
public final class TombstoneListener implements Listener {

    /** Carried through FallingBlockCasts into %damage% to select Tombstone's IMPACT ability. */
    public static final double IMPACT_MARKER = -1010.0;
    private static final String ENCHANT = "enchants/tombstone";
    private static final String DRAGON_SLAYER = "sets/dragon-slayer";

    private final SinkFactory sinks;
    private final SinkEnv env;
    private final ProtectionService protection;
    private final int jump;
    private final int slow;
    private final int enchantment;
    private final int portal;
    private final int anvil;
    private final int anvilLand;
    private final int witherHurt;

    public TombstoneListener(SinkFactory sinks, SinkEnv env, ProtectionService protection,
                             RegistryResolvers resolvers) {
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        this.protection = Objects.requireNonNull(protection, "protection");
        Objects.requireNonNull(resolvers, "resolvers");
        jump = resolvers.potionEffect("JUMP").orElse(-1);
        slow = resolvers.potionEffect("SLOW").orElse(-1);
        enchantment = resolvers.particle("ENCHANTMENT_TABLE").orElse(-1);
        portal = resolvers.particle("PORTAL").orElse(-1);
        anvil = resolvers.material("ANVIL").orElse(-1);
        anvilLand = resolvers.sound("ANVIL_LAND").orElse(-1);
        witherHurt = resolvers.sound("WITHER_HURT").orElse(-1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDefense(EntityDamageByEntityEvent event) {
        Player attacker = MarkOfTheBeastListener.resolvePlayerForCosmic(event.getDamager());
        if (!(event.getEntity() instanceof Player wearer) || attacker == null
                || !CosmicTierGate.tierSixPlusEnabled(wearer)
                || !CosmicDefenseGate.sourceCombatCause(event.getCause())
                || CosmicDefenseGate.silenced(wearer, env)) {
            return;
        }
        int level = EnchantLevels.worn(wearer, ENCHANT);
        if (level <= 0 || level > 10
                || ThreadLocalRandom.current().nextDouble() > 0.02 * (level / 3.0)) {
            return;
        }

        int cap = 4 + level / 2;
        double radius = 6 + level;
        int affected = 0;
        SinkReadback sink = sinks.create(env);
        if (anvilLand >= 0) {
            sink.privateSound(wearer, anvilLand, 4.0f, 1.9f);
        }
        for (Entity nearby : wearer.getNearbyEntities(radius, radius, radius)) {
            if (affected >= cap) {
                break;
            }
            if (!(nearby instanceof Player target) || target.equals(wearer)
                    || target.getGameMode() != GameMode.SURVIVAL
                    || CombatDispatch.friendly(wearer, target)
                    || !protection.allows(wearer.getUniqueId(), target.getLocation())) {
                continue;
            }
            affected++;
            sink.movementSpeed(target, 0.0, 60);
            if (jump >= 0) {
                sink.potionForce(target, jump, 128, 60);
            }
            if (slow >= 0) {
                sink.potionForce(target, slow, 128, 60);
            }
            if (witherHurt >= 0) {
                sink.privateSound(target, witherHurt, 1.0f, 0.25f);
            }

            boolean silenceBlocked = ActiveSets.has(target, DRAGON_SLAYER)
                    && ThreadLocalRandom.current().nextDouble() < 0.75;
            if (silenceBlocked) {
                sink.message(target, "&c&l* SILENCE BLOCKED [&7" + wearer.getName() + "&c&l] *");
            } else {
                env.stores().suppression().suppressDefense(target.getUniqueId(), env.nowTicks().getAsLong(), 80, -1);
                particles(sink, target, 20);
                Scheduling.onEntityLater(target, 80L, () -> {
                    SinkReadback ending = sinks.create(env);
                    particles(ending, target, 30);
                    ending.flush();
                });
            }
            sink.message(target, "&5&l* TOMBSTONE (&7" + wearer.getName() + " &7[4s]&5&l) *");
            if (anvilLand >= 0) {
                sink.privateSoundAt(target, target.getLocation().clone().add(0.0, 5.0, 0.0),
                        anvilLand, 3.0f, 1.1f);
            }
            if (anvil >= 0) {
                sink.fallingBlock(target.getLocation().clone().add(0.0, 5.0, 0.0), anvil, 120,
                        wearer.getUniqueId(), target.getUniqueId(), IMPACT_MARKER);
            }
        }
        sink.message(wearer, "&c&l* TOMBSTONE [&7" + affected + " players&c&l] *");
        sink.flush();
    }

    private void particles(SinkReadback sink, Player target, int portalCount) {
        if (enchantment >= 0) {
            sink.particle(target.getEyeLocation(), enchantment, 20, -1, 0.4, 0.4, 0.4, 0.0);
        }
        if (portal >= 0) {
            sink.particle(target.getEyeLocation(), portal, portalCount, -1, 0.4, 0.4, 0.4, 0.0);
        }
    }
}
