package feature.trigger;

import compile.load.ContentHolder;
import compile.model.Snapshot;
import engine.run.AbilityExecutor;
import engine.run.ActivationContext;
import engine.sink.DispatchSink;
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
    private final IntPredicate attackTrigger;

    public final int mine;
    public final int kill;
    public final int fall;
    public final int fire;
    public final int interact;
    public final int interactLeft;
    public final int interactRight;

    /** Trigger dispatch with no economy (money effects on non-combat triggers are no-ops). */
    public TriggerDispatch(AbilityExecutor executor, RuntimeHandles handles, ContentHolder content,
                           WornStateStore worn, TriggerRegistry triggers, LongSupplier nowTicks,
                           Function<Player, Optional<SoulBinding>> soulBinder) {
        this(executor, handles, content, worn, triggers, nowTicks, soulBinder, EconomyService.NONE);
    }

    /** Trigger dispatch with an economy: GIVE_MONEY/TAKE_MONEY on MINE/KILL/… deposit/withdraw via the sink. */
    public TriggerDispatch(AbilityExecutor executor, RuntimeHandles handles, ContentHolder content,
                           WornStateStore worn, TriggerRegistry triggers, LongSupplier nowTicks,
                           Function<Player, Optional<SoulBinding>> soulBinder, EconomyService economy) {
        this.runner = new TriggerRunner(executor, worn, soulBinder, nowTicks);
        this.handles = Objects.requireNonNull(handles, "handles");
        this.content = Objects.requireNonNull(content, "content");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.attackTrigger = triggers.attackTriggers();
        this.mine = triggers.idOf("MINE").orElse(-1);
        this.kill = triggers.idOf("KILL").orElse(-1);
        this.fall = triggers.idOf("FALL").orElse(-1);
        this.fire = triggers.idOf("FIRE").orElse(-1);
        this.interact = triggers.idOf("INTERACT").orElse(-1);
        this.interactLeft = triggers.idOf("INTERACT_LEFT").orElse(-1);
        this.interactRight = triggers.idOf("INTERACT_RIGHT").orElse(-1);
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
        DispatchSink sink = new DispatchSink(handles, economy);
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
        DispatchSink sink = new DispatchSink(handles, economy);
        runner.run(snapshot.abilities(), snapshot.generation(), worldId(snapshot, context), triggerId,
                attackTrigger.test(triggerId), actor, context, sink, snapshot.stableKeys());
        event.setDamage(sink.fold().apply(event.getDamage()));
        if (sink.cancelled()) {
            event.setCancelled(true);
        }
        sink.flush();
    }

    private static int worldId(Snapshot snapshot, ActivationContext context) {
        Location at = context.location();
        return TriggerRunner.worldId(snapshot, at == null ? null : at.getWorld());
    }
}
