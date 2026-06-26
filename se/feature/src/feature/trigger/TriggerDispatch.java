package feature.trigger;

import compile.load.ContentHolder;
import compile.model.Ability;
import compile.model.Snapshot;
import engine.run.AbilityExecutor;
import engine.run.ActivationContext;
import engine.run.FactPopulator;
import engine.sink.SinkReadback;
import engine.sink.SoulDebit;
import engine.stores.ImmuneStore;
import engine.stores.KeepOnDeathStore;
import engine.stores.KnockbackControlStore;
import engine.stores.SuppressionStore;
import engine.stores.TeleblockStore;
import engine.stores.VarStore;
import engine.trigger.TriggerRegistry;
import feature.soul.SoulBinding;
import item.worn.WornStateStore;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.LongSupplier;
import feature.combat.MineDrops;
import feature.combat.ProjectileHoming;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import platform.economy.EconomyService;
import engine.sink.SinkFactory;

/**
 * Dispatches the NON-combat triggers (§3.3) — MINE, KILL, FALL, FIRE, INTERACT* — that {@code CombatDispatch}
 * (ATTACK/DEFENSE on {@code EntityDamageByEntityEvent}) does not cover. Routes the actor's worn abilities
 * through the shared {@link TriggerRunner} into a per-event {@link SinkReadback}, then applies its read-backs:
 * a neutral event ({@link #fire}) honours only a {@code cancelEvent}; a damage event ({@link #fireDamage})
 * also folds the accumulated deltas onto it. Trigger ids resolve once at construction; an absent trigger is
 * {@code -1} and its {@code fire} is a no-op.
 */
public final class TriggerDispatch {

    private final TriggerRunner runner;
    private final AbilityExecutor executor; // §B lifecycle runs effects gatelessly, outside the runner's gate path
    private final SinkFactory sinkFactory;
    private final ContentHolder content;
    private final EconomyService economy;
    private final SoulDebit souls;
    private final VarStore vars;
    private final SuppressionStore suppression;
    private final KnockbackControlStore knockback;
    private final KeepOnDeathStore keepOnDeath;
    private final TeleblockStore teleblock;
    private final ImmuneStore immune;
    private final LongSupplier nowTicks;
    private final java.util.function.DoubleSupplier maxHeroicOutgoing; // §F config.yml heroic.max-outgoing-factor
    private final IntPredicate attackTrigger;

    public final int mine;
    public final int kill;
    public final int fall;
    public final int fire;
    public final int interact;
    public final int interactLeft;
    public final int interactRight;
    public final int death;
    public final int bowFire;
    public final int fishing;
    public final int eat;
    public final int itemDamage;
    public final int breakItem;
    public final int repeating;
    public final int held;    // §B HELD lifecycle — fired by the LifecycleDriver, not a Bukkit listener
    public final int passive; // §B PASSIVE lifecycle — fired by the LifecycleDriver, not a Bukkit listener
    public final int command; // §B COMMAND — fired by the configured CommandTriggerCommand

    /** Trigger dispatch with no economy (money effects on non-combat triggers are no-ops). */
    public TriggerDispatch(AbilityExecutor executor, SinkFactory sinkFactory, ContentHolder content,
                           WornStateStore worn, TriggerRegistry triggers, LongSupplier nowTicks,
                           Function<Player, Optional<SoulBinding>> soulBinder) {
        this(executor, sinkFactory, content, worn, triggers, nowTicks, soulBinder, EconomyService.NONE,
                SoulDebit.NONE, new VarStore(), new SuppressionStore(), new KnockbackControlStore(),
                new KeepOnDeathStore());
    }

    /** Trigger dispatch with an economy: GIVE_MONEY/TAKE_MONEY on MINE/KILL/… deposit/withdraw via the sink. */
    public TriggerDispatch(AbilityExecutor executor, SinkFactory sinkFactory, ContentHolder content,
                           WornStateStore worn, TriggerRegistry triggers, LongSupplier nowTicks,
                           Function<Player, Optional<SoulBinding>> soulBinder, EconomyService economy,
                           SoulDebit souls, VarStore vars, SuppressionStore suppression,
                           KnockbackControlStore knockback, KeepOnDeathStore keepOnDeath) {
        this(executor, sinkFactory, content, worn, triggers, nowTicks, soulBinder, economy, souls, vars,
                suppression, knockback, keepOnDeath,
                () -> engine.interact.DamageFold.DEFAULT_MAX_HEROIC_OUTGOING_FACTOR);
    }

    /** As the heroic-ceiling ctor, additionally sharing the TELEBLOCK/IMMUNE stores with their listeners. */
    public TriggerDispatch(AbilityExecutor executor, SinkFactory sinkFactory, ContentHolder content,
                           WornStateStore worn, TriggerRegistry triggers, LongSupplier nowTicks,
                           Function<Player, Optional<SoulBinding>> soulBinder, EconomyService economy,
                           SoulDebit souls, VarStore vars, SuppressionStore suppression,
                           KnockbackControlStore knockback, KeepOnDeathStore keepOnDeath,
                           java.util.function.DoubleSupplier maxHeroicOutgoing) {
        this(executor, sinkFactory, content, worn, triggers, nowTicks, soulBinder, economy, souls, vars,
                suppression, knockback, keepOnDeath, new TeleblockStore(), new ImmuneStore(), maxHeroicOutgoing);
    }

    /**
     * Full ctor plus the live heroic outgoing-damage ceiling (config.yml {@code heroic.max-outgoing-factor},
     * §F) threaded into each per-event {@link SinkReadback}, read live so a reload re-tunes it.
     */
    public TriggerDispatch(AbilityExecutor executor, SinkFactory sinkFactory, ContentHolder content,
                           WornStateStore worn, TriggerRegistry triggers, LongSupplier nowTicks,
                           Function<Player, Optional<SoulBinding>> soulBinder, EconomyService economy,
                           SoulDebit souls, VarStore vars, SuppressionStore suppression,
                           KnockbackControlStore knockback, KeepOnDeathStore keepOnDeath,
                           TeleblockStore teleblock, ImmuneStore immune,
                           java.util.function.DoubleSupplier maxHeroicOutgoing) {
        this.sinkFactory = Objects.requireNonNull(sinkFactory, "sinkFactory");
        this.content = Objects.requireNonNull(content, "content");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.souls = Objects.requireNonNull(souls, "souls");
        this.vars = Objects.requireNonNull(vars, "vars");
        this.suppression = Objects.requireNonNull(suppression, "suppression");
        this.knockback = Objects.requireNonNull(knockback, "knockback");
        this.keepOnDeath = Objects.requireNonNull(keepOnDeath, "keepOnDeath");
        this.teleblock = Objects.requireNonNull(teleblock, "teleblock");
        this.immune = Objects.requireNonNull(immune, "immune");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        this.maxHeroicOutgoing = Objects.requireNonNull(maxHeroicOutgoing, "maxHeroicOutgoing");
        // Conditions read through a VarStore-backed populator so a %name% can read an earlier SET_VAR write.
        this.runner = new TriggerRunner(executor, worn, soulBinder, nowTicks, FactPopulator.builtin(vars));
        this.executor = Objects.requireNonNull(executor, "executor");
        this.attackTrigger = triggers.attackTriggers();
        this.mine = triggers.idOf("MINE").orElse(-1);
        this.kill = triggers.idOf("KILL").orElse(-1);
        this.fall = triggers.idOf("FALL").orElse(-1);
        this.fire = triggers.idOf("FIRE").orElse(-1);
        this.interact = triggers.idOf("INTERACT").orElse(-1);
        this.interactLeft = triggers.idOf("INTERACT_LEFT").orElse(-1);
        this.interactRight = triggers.idOf("INTERACT_RIGHT").orElse(-1);
        this.death = triggers.idOf("DEATH").orElse(-1);
        this.bowFire = triggers.idOf("BOW_FIRE").orElse(-1);
        this.fishing = triggers.idOf("FISHING").orElse(-1);
        this.eat = triggers.idOf("EAT").orElse(-1);
        this.itemDamage = triggers.idOf("ITEM_DAMAGE").orElse(-1);
        this.breakItem = triggers.idOf("BREAK").orElse(-1);
        this.repeating = triggers.idOf("REPEATING").orElse(-1);
        this.held = triggers.idOf("HELD").orElse(-1);
        this.passive = triggers.idOf("PASSIVE").orElse(-1);
        this.command = triggers.idOf("COMMAND").orElse(-1);
    }

    /**
     * Fire a neutral (non-damage) trigger. {@code cancellable} (the firing event, or {@code null}) is cancelled
     * iff an effect asked for it. The heroic fold is inert here (no damage event reads it).
     */
    public void fire(Player actor, int triggerId, ActivationContext context, Cancellable cancellable) {
        if (triggerId < 0) {
            return;
        }
        Snapshot snapshot = content.snapshot();
        SinkReadback sink = newSink();
        runner.run(snapshot.abilities(), snapshot.generation(), worldId(snapshot, context), triggerId,
                attackTrigger.test(triggerId), actor, context, sink, snapshot.stableKeys());
        if (cancellable != null && sink.cancelled()) {
            cancellable.setCancelled(true);
        }
        sink.flush();
    }

    /**
     * Fire MINE for a block break, then apply the drop read-backs to {@code event}: {@code cancelEvent}, plus
     * {@code SMELT} / {@code TELEPORT_DROPS} (Cosmic Enchants-style parity). Runs on the block's region thread.
     */
    public void fireMine(Player actor, ActivationContext context, BlockBreakEvent event) {
        if (mine < 0) {
            return;
        }
        Snapshot snapshot = content.snapshot();
        SinkReadback sink = newSink();
        runner.run(snapshot.abilities(), snapshot.generation(), worldId(snapshot, context), mine,
                attackTrigger.test(mine), actor, context, sink, snapshot.stableKeys());
        if (sink.cancelled()) {
            event.setCancelled(true);
        } else {
            MineDrops.apply(event, sink.smeltRequested(), sink.teleportDropsRequested());
        }
        sink.flush();
    }

    /**
     * Fire BOW_FIRE for a bow shot, then home the projectile onto the nearest line-of-sight target if a
     * {@code SEEK} proc asked for it (steering runs on the projectile's entity scheduler — Folia-correct).
     * Honours a {@code cancelEvent}.
     */
    public void fireBow(Player shooter, ActivationContext context, EntityShootBowEvent event) {
        if (bowFire < 0) {
            fire(shooter, bowFire, context, event);
            return;
        }
        Snapshot snapshot = content.snapshot();
        SinkReadback sink = newSink();
        runner.run(snapshot.abilities(), snapshot.generation(), worldId(snapshot, context), bowFire,
                attackTrigger.test(bowFire), shooter, context, sink, snapshot.stableKeys());
        if (sink.cancelled()) {
            event.setCancelled(true);
        } else if (sink.seekRequested() && event.getProjectile() instanceof Projectile projectile) {
            ProjectileHoming.start(shooter, projectile);
        }
        sink.flush();
    }

    /**
     * Fire a damage-direction trigger (FALL/FIRE — defender side) and fold the deltas onto {@code event}, so a
     * defensive/heroic reduction actually softens the damage.
     */
    public void fireDamage(Player actor, int triggerId, ActivationContext context,
                           org.bukkit.event.entity.EntityDamageEvent event, boolean applyHeroic) {
        if (triggerId < 0) {
            return;
        }
        Snapshot snapshot = content.snapshot();
        SinkReadback sink = newSink();
        runner.run(snapshot.abilities(), snapshot.generation(), worldId(snapshot, context), triggerId,
                attackTrigger.test(triggerId), actor, context, sink, snapshot.stableKeys(), applyHeroic);
        event.setDamage(sink.fold().apply(event.getDamage()));
        if (sink.cancelled()) {
            event.setCancelled(true);
        }
        sink.flush();
    }

    /**
     * Fold ONLY the worn heroic reduction onto {@code event} — environmental damage with no trigger, softened
     * by heroic under {@code reduction-scope: ALL} (§F). Runs no trigger abilities.
     */
    public void fireEnvironmentalHeroic(Player actor, org.bukkit.event.entity.EntityDamageEvent event) {
        Snapshot snapshot = content.snapshot();
        SinkReadback sink = newSink();
        runner.contributeHeroicReduction(snapshot.generation(), actor, sink);
        event.setDamage(sink.fold().apply(event.getDamage()));
        sink.flush();
    }

    /**
     * Fire ONE repeating ability — the §B {@link feature.trigger.RepeatingDriver}'s timer body, on the player's
     * entity thread. Runs just that ability through the full gate sequence; chance/cooldown/condition gates
     * still apply each period. No firing event, so nothing to cancel.
     */
    public void fireRepeating(Player actor, int abilityId) {
        if (repeating < 0 || abilityId < 0) {
            return;
        }
        Snapshot snapshot = content.snapshot();
        ActivationContext context = new ActivationContext(actor, null, null, actor.getLocation());
        SinkReadback sink = newSink();
        runner.runCandidates(snapshot.abilities(), snapshot.generation(), worldId(snapshot, context),
                repeating, false, actor, context, sink, snapshot.stableKeys(), new int[]{abilityId});
        sink.flush();
    }

    /**
     * Fire the HELD/PASSIVE lifecycle transition (§B) on the player's entity thread, into ONE sink. NOT gated:
     * a maintained buff is deterministic. Ordering: STOP before START (level-swap removes the old buff before
     * re-applying); STOP is unconditional (a buff can never leak); START honours only the world-blacklist.
     */
    public void fireLifecycle(Player actor, List<Ability> stops, List<Ability> starts) {
        if (held < 0 && passive < 0) {
            return;
        }
        if (stops.isEmpty() && starts.isEmpty()) {
            return;
        }
        Snapshot snapshot = content.snapshot();
        ActivationContext context = new ActivationContext(actor, null, null, actor.getLocation());
        int worldId = worldId(snapshot, context);
        SinkReadback sink = newSink();
        for (Ability ability : stops) {
            executor.runLifecycle(ability, context, sink, true); // teardown=true: unconditional, never world-gated
        }
        for (Ability ability : starts) {
            if (!ability.blockedInWorld(worldId)) { // gate-1 only: a world-disabled passive does not turn on
                executor.runLifecycle(ability, context, sink, false);
            }
        }
        sink.flush();
    }

    /**
     * Fire the §B COMMAND trigger — the body of the configured {@code CommandTriggerCommand}. A normal triggered
     * activation: runs the full gate sequence (chance/cooldown/condition/souls) like any other trigger, only the
     * entry point differs. Neutral, so the heroic fold is inert.
     */
    public void fireCommand(Player actor) {
        fire(actor, command, new ActivationContext(actor, null, null, actor.getLocation()), null);
    }

    private SinkReadback newSink() {
        return sinkFactory.create(economy, souls, vars, suppression, knockback, keepOnDeath,
                teleblock, immune, nowTicks, maxHeroicOutgoing);
    }

    private static int worldId(Snapshot snapshot, ActivationContext context) {
        Location at = context.location();
        return TriggerRunner.worldId(snapshot, at == null ? null : at.getWorld());
    }
}
