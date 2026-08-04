package feature.mask;

import compile.load.ContentHolder;
import compile.model.Ability;
import compile.model.CompiledEffect;
import compile.model.Snapshot;
import engine.stores.SuppressionStore;
import item.worn.WornState;
import item.worn.WornStateStore;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * The {@code SPAWNER_YIELD} worn channel's consumer: each spawner spawn asks whether a wearer is in scope and,
 * if the roll wins, drops extra copies of the same mob at the same spot.
 *
 * <p>The wearer test is PER SPAWN against live worn state (deviation D-11-10 — the reference's per-chunk cache
 * is a bug: it kept paying a wearer who had walked off and refused one who had just arrived). Grants do not
 * stack; the strongest in scope wins, so a crowd of wearers cannot multiply one spawner.
 *
 * <p>{@code CreatureSpawnEvent} fires on the spawn location's own region thread and the extra copies land at
 * that same location, so {@code spawnEntity} is inline-safe — no hop. Those copies spawn as {@code CUSTOM},
 * which this handler filters out, so a winning roll cannot cascade.
 */
public final class SpawnerYieldListener implements Listener {

    /** The strongest grant in scope: the roll and how many copies it adds. */
    record Grant(double chancePercent, int extra) {

        static final Grant NONE = new Grant(0.0, 0);

        /** Expected added copies — the ordering key, so a bigger multiplier beats a likelier smaller one. */
        double weight() {
            return chancePercent * extra;
        }
    }

    private final ContentHolder content;
    private final WornStateStore worn;
    private final SuppressionStore suppression;
    private final LongSupplier nowTicks;
    private final BooleanSupplier enabled;
    private final Random rolls; // injected: ThreadLocalRandom is unstubbable through JvmDowngrader on 1.8
    private final int passive;

    public SpawnerYieldListener(ContentHolder content, WornStateStore worn, SuppressionStore suppression,
                                LongSupplier nowTicks, BooleanSupplier enabled, Random rolls, int passive) {
        this.content = Objects.requireNonNull(content, "content");
        this.worn = Objects.requireNonNull(worn, "worn");
        this.suppression = Objects.requireNonNull(suppression, "suppression");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.rolls = Objects.requireNonNull(rolls, "rolls");
        this.passive = passive;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        // Cheapest rejects first: the reason is a field compare, and everything below walks entities.
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER || passive < 0
                || !enabled.getAsBoolean()) {
            return;
        }
        LivingEntity spawned = event.getEntity();
        Location at = spawned.getLocation();
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        Grant grant = strongestInScope(at);
        if (grant.extra() <= 0 || rolls.nextDouble() * 100.0 >= grant.chancePercent()) {
            return; // strict <: chance 100 always wins, chance 0 never does
        }
        for (int i = 0; i < grant.extra(); i++) {
            world.spawnEntity(at, spawned.getType());
        }
    }

    /** The best grant any player standing in scope of {@code at} carries, or {@link Grant#NONE}. */
    private Grant strongestInScope(Location at) {
        Snapshot snapshot = content.snapshot();
        long now = nowTicks.getAsLong();
        Grant best = Grant.NONE;
        // The spawn's own chunk is the firing region's, so this entity walk is region-local on Folia. A radius
        // grant is resolved from the same walk: a wearer further out than a chunk cannot be reached without a
        // cross-region scan, and paying only the chunk's own players is the conservative direction.
        for (Entity nearby : at.getChunk().getEntities()) {
            if (!(nearby instanceof Player wearer)) {
                continue;
            }
            Grant grant = grantOf(worn.get(wearer.getUniqueId()), snapshot, suppression, wearer.getUniqueId(),
                    now, passive, at, wearer.getLocation());
            if (grant.weight() > best.weight()) {
                best = grant;
            }
        }
        return best;
    }

    /**
     * The strongest {@code SPAWNER_YIELD} a wearer's live PASSIVE abilities grant for a spawn at {@code at},
     * or {@link Grant#NONE}. Pure (no Bukkit state beyond the two positions); a stale or absent worn state,
     * a suppressed source, or a wearer outside a {@code radius} grant's reach all yield nothing.
     */
    static Grant grantOf(WornState state, Snapshot snapshot, SuppressionStore suppression, UUID player,
                         long now, int passive, Location spawn, Location wearer) {
        if (passive < 0 || state == null || state.gen() != snapshot.generation()) {
            return Grant.NONE;
        }
        Ability[] abilities = snapshot.abilities();
        Grant best = Grant.NONE;
        for (int abilityId : state.byTrigger(passive)) {
            if (abilityId < 0 || abilityId >= abilities.length) {
                continue;
            }
            Ability ability = abilities[abilityId];
            if (suppression.suppressesAny(ability, player, now)) {
                continue; // a DISABLE'd source grants nothing; the window's end restores it
            }
            for (CompiledEffect effect : ability.effects()) {
                if (!"SPAWNER_YIELD".equals(effect.head())) {
                    continue;
                }
                if (!inScope(effect.args().str("scope"), effect.args().dbl("radius"), spawn, wearer)) {
                    continue;
                }
                Grant grant = new Grant(effect.args().dbl("chance"), effect.args().integer("extra"));
                if (grant.weight() > best.weight()) {
                    best = grant;
                }
            }
        }
        return best;
    }

    /** {@code chunk} scope is satisfied by the caller's own walk; {@code radius} additionally measures. */
    private static boolean inScope(String scope, double radius, Location spawn, Location wearer) {
        if (!"radius".equalsIgnoreCase(scope)) {
            return true;
        }
        return wearer != null && spawn.getWorld() != null && spawn.getWorld().equals(wearer.getWorld())
                && spawn.distanceSquared(wearer) <= radius * radius;
    }
}
