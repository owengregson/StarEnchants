package feature.combat;

import engine.effect.kind.EnchantLevels;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import feature.soul.SoulService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import platform.resolve.RegistryResolvers;

/** Native, bug-corrected implementation of Cosmic Paradox. */
public final class ParadoxListener implements Listener {

    private static final String ENCHANT = "enchants/paradox";

    private final SinkFactory sinks;
    private final SinkEnv env;
    private final SoulService souls;
    private final int happyVillager;
    private final int lava;
    private final int eat;
    private final int levelUp;
    private final int itemBreak;

    public ParadoxListener(SinkFactory sinks, SinkEnv env, SoulService souls, RegistryResolvers resolvers) {
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        this.souls = Objects.requireNonNull(souls, "souls");
        Objects.requireNonNull(resolvers, "resolvers");
        happyVillager = resolvers.particle("VILLAGER_HAPPY").orElse(-1);
        lava = resolvers.particle("LAVA").orElse(-1);
        eat = resolvers.sound("EAT").orElse(-1);
        levelUp = resolvers.sound("LEVEL_UP").orElse(-1);
        itemBreak = resolvers.sound("ITEM_BREAK").orElse(-1);
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDefense(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player wearer)
                || !CosmicTierGate.tierSixPlusEnabled(wearer)
                || !CosmicDefenseGate.sourceCombatCause(event.getCause())
                || CosmicDefenseGate.silenced(wearer, env)) {
            return;
        }
        int level = EnchantLevels.worn(wearer, ENCHANT);
        if (level <= 0 || level > 5 || ThreadLocalRandom.current().nextDouble() >= 0.1 * level) {
            return;
        }
        long now = env.nowTicks().getAsLong();
        if (env.stores().vars().get(wearer.getUniqueId(), "soul-trapped", now) != null) {
            return;
        }

        // The source accidentally used integer division (level / 10), making every legal level heal zero.
        double heal = event.getDamage() * level / 10.0;
        double horizontal = 8 + level * 4;
        List<Player> allies = new ArrayList<>();
        boolean someoneCanReceiveFullHeal = false;
        for (Entity nearby : wearer.getNearbyEntities(horizontal, horizontal + 128.0, horizontal)) {
            if (!(nearby instanceof Player ally) || ally.hasMetadata("spectator")
                    || ally.getGameMode() != GameMode.SURVIVAL
                    || !CombatDispatch.friendly(wearer, ally)) {
                continue;
            }
            allies.add(ally);
            if (ally.getHealth() + heal <= ally.getMaxHealth()) {
                someoneCanReceiveFullHeal = true;
            }
        }
        if (!someoneCanReceiveFullHeal) {
            return;
        }

        int cost = 5; // external outpost/faction discount systems are intentionally outside SE's boundary.
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

        SinkReadback sink = sinks.create(env);
        for (Player ally : allies) {
            if (happyVillager >= 0) {
                sink.particle(ally.getLocation().add(0.0, 1.0, 0.0), happyVillager, 20,
                        -1, 0.7, 0.7, 0.7, 0.0);
            }
            sink.heal(ally, heal);
            if (eat >= 0) {
                sink.privateSound(ally, eat, 1.0f, 2.0f);
            }
            sink.message(ally, "&a&l** PARADOX [" + wearer.getName() + "] (+" + heal + "HP) **");
        }
        sink.message(wearer, "&2&l** PARADOX [" + (allies.size() * heal) + " -> HP]  **");
        int remaining = souls.currentTotal(wearer);
        if (remaining % 100 == 0 || remaining < 10) {
            sink.message(wearer, "&c&l- " + cost + " Soul Gems");
            sink.message(wearer, "&7You have &n" + remaining + "&7 souls left.");
        }
        if (levelUp >= 0) {
            sink.privateSound(wearer, levelUp, 1.0f, 0.65f);
        }
        sink.flush();
    }
}
