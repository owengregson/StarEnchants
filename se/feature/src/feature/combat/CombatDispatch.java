package feature.combat;

import compile.load.ContentHolder;
import compile.model.Ability;
import compile.model.Snapshot;
import engine.run.AbilityExecutor;
import engine.run.ActivationContext;
import engine.sink.DispatchSink;
import feature.soul.SoulBinding;
import feature.trigger.TriggerRunner;
import item.worn.WornStateStore;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongSupplier;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import platform.resolve.RuntimeHandles;

/**
 * Turns a Bukkit combat event into ability activations (docs/architecture.md §3.3, §3.6) — the
 * convergence of the whole runtime spine. On an {@link EntityDamageByEntityEvent} it gathers the
 * attacker's {@code ATTACK} abilities and the defender's {@code DEFENSE} abilities from their
 * PRE-RESOLVED {@link WornState}s (read-only — never re-resolving a cross-region entity, §3.4), runs
 * each through the {@link AbilityExecutor} into one per-event {@link DispatchSink}, folds the
 * accumulated damage deltas onto the event a single time (§6.1), honours a {@code cancelEvent}, and
 * flushes the deferred world mutations batched per owning thread.
 *
 * <p>The event handler runs on the firing region thread (the victim's region on Folia); damage
 * folding and cancellation are synchronous read-backs there, while world-mutating effects are routed
 * to their owning threads by the Sink. The actor's {@code RuntimeHandles} are paired with the
 * snapshot's compile resolver so handle-using effects (potions, spawns) resolve correctly.
 */
public final class CombatDispatch {

    private final TriggerRunner runner;
    private final RuntimeHandles handles;
    private final ContentHolder content;
    private final int attackTriggerId;
    private final int defenseTriggerId;

    /** Combat dispatch with NO soul system (the soul gate is never armed). */
    public CombatDispatch(AbilityExecutor executor, RuntimeHandles handles, ContentHolder content,
                          WornStateStore worn, int attackTriggerId, int defenseTriggerId,
                          LongSupplier nowTicks) {
        this(executor, handles, content, worn, attackTriggerId, defenseTriggerId, nowTicks,
                actor -> Optional.empty());
    }

    /** Combat dispatch with a soul binder: an actor in soul mode arms gate 10 from their active gem. */
    public CombatDispatch(AbilityExecutor executor, RuntimeHandles handles, ContentHolder content,
                          WornStateStore worn, int attackTriggerId, int defenseTriggerId,
                          LongSupplier nowTicks, Function<Player, Optional<SoulBinding>> soulBinder) {
        this.runner = new TriggerRunner(executor, worn, soulBinder, nowTicks);
        this.handles = Objects.requireNonNull(handles, "handles");
        this.content = Objects.requireNonNull(content, "content");
        this.attackTriggerId = attackTriggerId;
        this.defenseTriggerId = defenseTriggerId;
    }

    /** Dispatch one entity-on-entity hit: run attacker + defender abilities and fold the result. */
    public void onDamage(EntityDamageByEntityEvent event) {
        Snapshot snapshot = content.snapshot();
        Ability[] abilities = snapshot.abilities();
        Entity damager = event.getDamager();
        // A projectile (bow/trident/snowball) attributes the hit to its shooter for ability purposes;
        // reading the projectile's shooter reference is safe (the projectile is in the firing region).
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        Entity victimEntity = event.getEntity();
        LivingEntity victim = victimEntity instanceof LivingEntity living ? living : null;
        LivingEntity attacker = damager instanceof LivingEntity living ? living : null;
        Location at = victimEntity.getLocation();
        int worldId = TriggerRunner.worldId(snapshot, victimEntity.getWorld());

        DispatchSink sink = new DispatchSink(handles);

        // Attack side: the player damager's ATTACK abilities act on the victim (self = the attacker).
        if (damager instanceof Player attackerPlayer) {
            runner.run(abilities, snapshot.generation(), worldId, attackTriggerId, true,
                    attackerPlayer, new ActivationContext(attackerPlayer, victim, null, at), sink,
                    snapshot.stableKeys());
        }
        // Defense side: the player victim's DEFENSE abilities retaliate against the attacker.
        if (victimEntity instanceof Player defenderPlayer) {
            runner.run(abilities, snapshot.generation(), worldId, defenseTriggerId, false,
                    defenderPlayer, new ActivationContext(defenderPlayer, attacker, attacker, at), sink,
                    snapshot.stableKeys());
        }

        // Fold every damage contribution onto the event ONCE (§6.1); honour a cancel; flush deferred work.
        event.setDamage(sink.fold().apply(event.getDamage()));
        if (sink.cancelled()) {
            event.setCancelled(true);
        }
        sink.flush();
    }
}
