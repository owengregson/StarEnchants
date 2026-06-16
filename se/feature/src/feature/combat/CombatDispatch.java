package feature.combat;

import compile.load.ContentHolder;
import compile.model.Ability;
import compile.model.Snapshot;
import engine.pipeline.Activation;
import engine.run.AbilityExecutor;
import engine.run.ActivationContext;
import engine.sink.DispatchSink;
import feature.soul.SoulBinding;
import item.worn.WornState;
import item.worn.WornStateStore;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.LongSupplier;
import org.bukkit.Location;
import org.bukkit.World;
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

    private final AbilityExecutor executor;
    private final RuntimeHandles handles;
    private final ContentHolder content;
    private final WornStateStore worn;
    private final int attackTriggerId;
    private final int defenseTriggerId;
    private final LongSupplier nowTicks;
    private final Function<Player, Optional<SoulBinding>> soulBinder;

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
        this.executor = Objects.requireNonNull(executor, "executor");
        this.handles = Objects.requireNonNull(handles, "handles");
        this.content = Objects.requireNonNull(content, "content");
        this.worn = Objects.requireNonNull(worn, "worn");
        this.attackTriggerId = attackTriggerId;
        this.defenseTriggerId = defenseTriggerId;
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        this.soulBinder = Objects.requireNonNull(soulBinder, "soulBinder");
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
        int worldId = worldId(snapshot, victimEntity.getWorld());
        long now = nowTicks.getAsLong();

        DispatchSink sink = new DispatchSink(handles);

        // Attack side: the player damager's ATTACK abilities act on the victim (self = the attacker).
        if (damager instanceof Player attackerPlayer) {
            runPass(abilities, snapshot, sink, worldId, now, attackTriggerId, true,
                    attackerPlayer, new ActivationContext(attackerPlayer, victim, null, at));
        }
        // Defense side: the player victim's DEFENSE abilities retaliate against the attacker.
        if (victimEntity instanceof Player defenderPlayer) {
            runPass(abilities, snapshot, sink, worldId, now, defenseTriggerId, false,
                    defenderPlayer, new ActivationContext(defenderPlayer, attacker, attacker, at));
        }

        // Fold every damage contribution onto the event ONCE (§6.1); honour a cancel; flush deferred work.
        event.setDamage(sink.fold().apply(event.getDamage()));
        if (sink.cancelled()) {
            event.setCancelled(true);
        }
        sink.flush();
    }

    private void runPass(Ability[] abilities, Snapshot snapshot, DispatchSink sink, int worldId, long now,
                         int triggerId, boolean attackSide, Player actor, ActivationContext context) {
        WornState wornState = worn.get(actor.getUniqueId());
        if (wornState == null || wornState.gen() != snapshot.generation()) {
            return; // not resolved yet (or stale across a reload) — this side contributes nothing
        }
        // Heroic flat stats are a PASSIVE source — not chance/trigger-gated — so they contribute once
        // per side regardless of whether any ability fires: the attacker's flat damage on the attack
        // side, the defender's flat reduction on the defense side (§6.1). A zero stat is a no-op fold.
        if (attackSide) {
            sink.addFlatDamage(wornState.heroic().flatDamage());
        } else {
            sink.addFlatReduction(wornState.heroic().flatReduction());
        }
        int[] candidates = wornState.byTrigger(triggerId);
        if (candidates.length == 0) {
            return;
        }
        Activation.Builder builder = Activation.builder(actor.getUniqueId(), worldId, triggerId, now)
                .chanceRoll(() -> ThreadLocalRandom.current().nextDouble(100.0));
        // Arm the soul gate from the actor's active gem (if any): a soul-cost ability spends from it.
        soulBinder.apply(actor).ifPresent(binding -> builder.soulMode(binding.gemId(), binding.balance()));
        executor.run(abilities, candidates, builder.build(), context, sink);
    }

    private static int worldId(Snapshot snapshot, World world) {
        // A world named in no blacklist interns to -1; Ability.blockedInWorld(-1) is false (never blocked).
        return world == null ? -1 : snapshot.interners().worlds().idOf(world.getName());
    }
}
