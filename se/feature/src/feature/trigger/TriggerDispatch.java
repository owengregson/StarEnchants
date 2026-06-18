package feature.trigger;

import compile.load.ContentHolder;
import compile.model.Snapshot;
import engine.run.AbilityExecutor;
import engine.run.ActivationContext;
import engine.run.FactPopulator;
import engine.sink.DispatchSink;
import engine.sink.SoulDebit;
import engine.stores.KnockbackControlStore;
import engine.stores.SuppressionStore;
import engine.stores.VarStore;
import engine.trigger.TriggerRegistry;
import feature.soul.SoulBinding;
import item.worn.WornStateStore;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.LongSupplier;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import platform.economy.EconomyService;
import platform.resolve.RuntimeHandles;

/**
 * Dispatches the NON-combat triggers (docs/architecture.md §3.3) — MINE, KILL, FALL, FIRE, INTERACT*
 * — that {@code CombatDispatch} (ATTACK/DEFENSE on {@code EntityDamageByEntityEvent}) does not cover.
 * Each thin Bukkit-event listener resolves the actor and the trigger, then this routes the actor's
 * worn abilities through the shared {@link TriggerRunner} into a per-event {@link DispatchSink}, and
 * applies the sink's read-backs to the firing event: a neutral event ({@link #fire}) only honours a
 * {@code cancelEvent}; a damage event ({@link #fireDamage}) also folds the accumulated deltas onto it.
 *
 * <p>Trigger ids are resolved once at construction; a trigger absent from the vocabulary resolves to
 * {@code -1} and its {@code fire} call is a no-op (the listener still registers, it just never acts).
 */
public final class TriggerDispatch {

    private final TriggerRunner runner;
    private final RuntimeHandles handles;
    private final ContentHolder content;
    private final EconomyService economy;
    private final SoulDebit souls;
    private final VarStore vars;
    private final SuppressionStore suppression;
    private final KnockbackControlStore knockback;
    private final LongSupplier nowTicks;
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

    /** Trigger dispatch with no economy (money effects on non-combat triggers are no-ops). */
    public TriggerDispatch(AbilityExecutor executor, RuntimeHandles handles, ContentHolder content,
                           WornStateStore worn, TriggerRegistry triggers, LongSupplier nowTicks,
                           Function<Player, Optional<SoulBinding>> soulBinder) {
        this(executor, handles, content, worn, triggers, nowTicks, soulBinder, EconomyService.NONE,
                SoulDebit.NONE, new VarStore(), new SuppressionStore(), new KnockbackControlStore());
    }

    /** Trigger dispatch with an economy: GIVE_MONEY/TAKE_MONEY on MINE/KILL/… deposit/withdraw via the sink. */
    public TriggerDispatch(AbilityExecutor executor, RuntimeHandles handles, ContentHolder content,
                           WornStateStore worn, TriggerRegistry triggers, LongSupplier nowTicks,
                           Function<Player, Optional<SoulBinding>> soulBinder, EconomyService economy,
                           SoulDebit souls, VarStore vars, SuppressionStore suppression,
                           KnockbackControlStore knockback) {
        this.handles = Objects.requireNonNull(handles, "handles");
        this.content = Objects.requireNonNull(content, "content");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.souls = Objects.requireNonNull(souls, "souls");
        this.vars = Objects.requireNonNull(vars, "vars");
        this.suppression = Objects.requireNonNull(suppression, "suppression");
        this.knockback = Objects.requireNonNull(knockback, "knockback");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        // Conditions read through a VarStore-backed populator so a %name% can read an earlier SET_VAR write.
        this.runner = new TriggerRunner(executor, worn, soulBinder, nowTicks, FactPopulator.builtin(vars));
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
    }

    /**
     * Fire a neutral (non-damage) trigger for {@code actor}. {@code cancellable} (the firing event,
     * or {@code null}) is cancelled iff an effect asked for it. Heroic/fold contributions are inert
     * here (no damage event reads the fold).
     */
    public void fire(Player actor, int triggerId, ActivationContext context, Cancellable cancellable) {
        if (triggerId < 0) {
            return;
        }
        Snapshot snapshot = content.snapshot();
        DispatchSink sink = new DispatchSink(handles, economy, souls, vars, suppression, knockback, nowTicks);
        runner.run(snapshot.abilities(), snapshot.generation(), worldId(snapshot, context), triggerId,
                attackTrigger.test(triggerId), actor, context, sink, snapshot.stableKeys());
        if (cancellable != null && sink.cancelled()) {
            cancellable.setCancelled(true);
        }
        sink.flush();
    }

    /**
     * Fire a damage-direction trigger (FALL/FIRE — defender side) and fold the accumulated deltas onto
     * {@code event}, so a defensive reduction/heroic-reduction actually softens the fall/fire damage.
     */
    public void fireDamage(Player actor, int triggerId, ActivationContext context,
                           org.bukkit.event.entity.EntityDamageEvent event) {
        if (triggerId < 0) {
            return;
        }
        Snapshot snapshot = content.snapshot();
        DispatchSink sink = new DispatchSink(handles, economy, souls, vars, suppression, knockback, nowTicks);
        runner.run(snapshot.abilities(), snapshot.generation(), worldId(snapshot, context), triggerId,
                attackTrigger.test(triggerId), actor, context, sink, snapshot.stableKeys());
        event.setDamage(sink.fold().apply(event.getDamage()));
        if (sink.cancelled()) {
            event.setCancelled(true);
        }
        sink.flush();
    }

    /**
     * Fire ONE repeating ability for {@code actor} — the §B {@link feature.trigger.RepeatingDriver}'s timer
     * body, run on the player's own (entity) thread. Builds a fresh per-tick sink, runs just that ability
     * through the full gate sequence on the REPEATING trigger, and flushes. No firing event ⇒ nothing to
     * cancel; chance/cooldown/condition gates still apply each period.
     */
    public void fireRepeating(Player actor, int abilityId) {
        if (repeating < 0 || abilityId < 0) {
            return;
        }
        Snapshot snapshot = content.snapshot();
        ActivationContext context = new ActivationContext(actor, null, null, actor.getLocation());
        DispatchSink sink = new DispatchSink(handles, economy, souls, vars, suppression, knockback, nowTicks);
        runner.runCandidates(snapshot.abilities(), snapshot.generation(), worldId(snapshot, context),
                repeating, false, actor, context, sink, snapshot.stableKeys(), new int[]{abilityId});
        sink.flush();
    }

    private static int worldId(Snapshot snapshot, ActivationContext context) {
        Location at = context.location();
        return TriggerRunner.worldId(snapshot, at == null ? null : at.getWorld());
    }
}
