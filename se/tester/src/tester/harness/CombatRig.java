package tester.harness;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;

/**
 * Shared staging + teardown for the combat-dispatch live suites (live-server-testing skill). Every such suite
 * repeats the same dance — force-load the arena chunk, drop to its region thread, spawn a clientless attacker
 * and a victim, act on the attacker's thread — and every one used to register a {@code CombatListener} that it
 * NEVER unregistered. Because the in-server {@link Harness} launches all scenarios on the same tick, a leaked
 * listener keeps dispatching on EVERY later suite's hit: a real cross-suite contamination (an inflated event
 * count, a stray knockback edit, a double proc). This fixture makes registration and actor spawning go through
 * one object that {@link #teardown()} unwinds, so isolation is the default, not a thing each suite remembers.
 *
 * <p><strong>Threading.</strong> {@link #onArena} mirrors the hand-written dance exactly (global → force-load →
 * region), so it carries no new thread risk. {@link #teardown()} unregisters listeners synchronously (so a
 * concurrent suite stops seeing them at once — the whole point) and removes each actor on its OWN region/entity
 * thread, which is Folia-correct even when actors were staged in distinct regions. All actor cleanup is
 * best-effort: teardown runs after the asserts, and a failed removal must never mask a result.
 */
public final class CombatRig {

    private final Plugin plugin;
    private final List<Listener> listeners = new ArrayList<>();
    private final List<Entity> actors = new ArrayList<>();

    public CombatRig(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Register {@code listener} AND track it, so {@link #teardown()} unregisters it — the leak fix. */
    public <T extends Listener> T listen(T listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listeners.add(listener);
        return listener;
    }

    /** Spawn + track a clientless fake player (must be called on the world's owning thread). */
    public Player spawnFake(World world, String name) {
        Player player = FakePlayers.spawn(world, name);
        actors.add(player);
        return player;
    }

    /** Spawn + track a live entity of {@code type} at {@code at}, cast to {@code as}. */
    public <E extends Entity> E spawn(World world, Location at, EntityType type, Class<E> as) {
        E entity = as.cast(world.spawnEntity(at, type));
        actors.add(entity);
        return entity;
    }

    /** Track an already-spawned actor so {@link #teardown()} removes it. */
    public <E extends Entity> E track(E actor) {
        actors.add(actor);
        return actor;
    }

    /** Force-load the chunk at {@code at}, then run {@code body} on that region's thread — the arena entry. */
    public void onArena(Location at, Runnable body) {
        World world = at.getWorld();
        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4, true);
            Scheduling.onRegion(at, body);
        });
    }

    /**
     * A two-chunk arena: force-load BOTH {@code primary} and {@code secondary}'s chunks, then run {@code body}
     * on {@code primary}'s region thread. Use when a victim is staged in a chunk distinct from the actor's so a
     * teleport/AoE genuinely crosses a Folia region boundary (the only place wrong-thread bugs surface).
     */
    public void onArena(Location primary, Location secondary, Runnable body) {
        World world = primary.getWorld();
        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(primary.getBlockX() >> 4, primary.getBlockZ() >> 4, true);
            world.setChunkForceLoaded(secondary.getBlockX() >> 4, secondary.getBlockZ() >> 4, true);
            Scheduling.onRegion(primary, body);
        });
    }

    /**
     * Unregister every tracked listener (synchronously, so concurrent suites stop seeing them immediately) and
     * remove every tracked actor on its own thread. Idempotent and best-effort.
     */
    public void teardown() {
        for (Listener listener : listeners) {
            try {
                HandlerList.unregisterAll(listener);
            } catch (Throwable ignored) {
                // cleanup must never mask a result; an already-gone listener is fine
            }
        }
        listeners.clear();
        for (Entity actor : List.copyOf(actors)) {
            if (actor instanceof Player player) {
                Scheduling.onEntity(player, () -> FakePlayers.despawn(player));
            } else {
                Scheduling.onEntity(actor, () -> {
                    try {
                        actor.remove();
                    } catch (Throwable ignored) {
                        // best-effort despawn
                    }
                });
            }
        }
        actors.clear();
    }
}
