package feature.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.load.ContentHolder;
import compile.model.Snapshot;
import engine.pipeline.Activation;
import engine.run.AbilityExecutor;
import engine.run.ActivationContext;
import engine.run.ActorProbe;
import engine.sink.EngineDamage;
import engine.sink.ModernDispatchSink;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.stores.DotSuppressionStore;
import engine.stores.EngineStores;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import feature.compat.DropControl;
import feature.compat.Hands;
import item.worn.WornStateStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import testfx.Abilities;
import testfx.Envs;
import testfx.Snapshots;
import testfx.SyncSchedulerBackend;
import testfx.WornStates;

/**
 * The {@code HURT} routing contract: an all-cause defender trigger has to reach EVERY damage-taken event
 * without firing twice for any of them, and without proc'ing off SE's own damage. Both failures are silent —
 * a missed cause means the ability just never fires, and a missing re-entrancy guard means a HURT ability that
 * deals damage recurses until the stack blows.
 */
class HurtDispatchTest {

    private static final UUID ACTOR = UUID.randomUUID();

    private final TriggerRegistry triggers = BuiltinTriggers.registry();
    private final int hurt = triggers.idOf("HURT").orElseThrow();
    private final int fall = triggers.idOf("FALL").orElseThrow();

    private AbilityExecutor executor;
    private TriggerListeners listeners;
    private EngineStores stores;

    @BeforeEach
    void setUp() {
        Scheduling.install(new SyncSchedulerBackend());
        RuntimeHandles handles = new RuntimeHandles(new RegistryResolvers());
        stores = EngineStores.fresh();
        SinkEnv env = Envs.sink().stores(stores).nowTicks(() -> 0L).build();
        SinkFactory sinkFactory = mock(SinkFactory.class);
        when(sinkFactory.create(any())).thenReturn(new ModernDispatchSink(handles, env));
        // The wearer carries one ability on EACH defender trigger, so which walks ran is visible.
        Snapshot snapshot = Snapshots.snapshot()
                .abilities(Abilities.ability().id(0).build(), Abilities.ability().id(1).build())
                .stableKeys("enchants/inversion/1", "enchants/nutrition/1")
                .build();
        ContentHolder content = mock(ContentHolder.class);
        when(content.snapshot()).thenReturn(snapshot);
        executor = mock(AbilityExecutor.class);
        WornStateStore worn = mock(WornStateStore.class);
        when(worn.get(ACTOR)).thenReturn(WornStates.worn().byTrigger(hurt, 0).byTrigger(fall, 1).build());
        TriggerDispatch dispatch = new TriggerDispatch(executor, sinkFactory, mock(ActorProbe.class), content,
                worn, triggers, player -> Optional.empty(), env, mock(Hands.class), mock(DropControl.class));
        listeners = new TriggerListeners(dispatch, mock(Hands.class));
    }

    @Test
    void anyEnvironmentalCauseFiresHurtWithTheCauseAndThePendingHitBound() {
        listeners.onEnvironmentalDamage(damage(EntityDamageEvent.DamageCause.POISON, 6.0, 4.0));

        ActivationContext context = onlyWalk(hurt);
        assertEquals(EntityDamageEvent.DamageCause.POISON.name(), context.damageCauseName());
        assertEquals(6.0, context.damage());
        assertEquals(4.0, context.vanillaFinalDamage(), "%posthit.health% prices against the vanilla-final hit");
        assertNull(context.attacker(), "an environmental hit has no attacker — HURT must stay null-safe");
        assertNull(context.victim());
    }

    @Test
    void aFallHitFiresBothFallAndHurtExactlyOnce() {
        listeners.onEnvironmentalDamage(damage(EntityDamageEvent.DamageCause.FALL, 6.0, 6.0));

        assertEquals(List.of(fall, hurt), walkedTriggers(2),
                "the cause trigger and HURT each walk once, in that order, over one shared fold");
    }

    @Test
    void engineIssuedDamageNeverProcsHurt() {
        EntityDamageEvent event = damage(EntityDamageEvent.DamageCause.CUSTOM, 3.0, 3.0);
        EngineDamage.frame(() -> listeners.onEnvironmentalDamage(event));

        verify(executor, never()).run(any(), any(), any(), any(), any(), any());
    }

    @Test
    void anEntityHitIsLeftToTheCombatPathSoHurtCannotDoubleFire() {
        // EntityDamageByEntityEvent shares EntityDamageEvent's handler list, so this handler sees it too;
        // CombatDispatch runs HURT there instead, beside DEFENSE, behind the gates only that path enforces.
        Player player = player();
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);

        listeners.onEnvironmentalDamage(event);

        verify(executor, never()).run(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aReplacedDotTickIsCancelledWhileTheBurnHoldsItsWindow() {
        // PERIODIC_DAMAGE's `replace` contract: the burn is the only damage clock, so the vanilla tick is
        // cancelled outright — never merely scaled, which would still hurt through the fold.
        stores.dotSuppression().suppress(ACTOR, DotSuppressionStore.CAUSE_WITHER, 0L, 100);
        EntityDamageEvent event = damage(EntityDamageEvent.DamageCause.WITHER, 2.0, 2.0);

        listeners.onEnvironmentalDamage(event);

        verify(event).setCancelled(true);
        verify(executor, never()).run(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aDotCauseTheBurnNeverNamedStillLands() {
        // The mask is per cause: a wither conversion must not quietly grant poison immunity too.
        stores.dotSuppression().suppress(ACTOR, DotSuppressionStore.CAUSE_WITHER, 0L, 100);
        EntityDamageEvent event = damage(EntityDamageEvent.DamageCause.POISON, 2.0, 2.0);

        listeners.onEnvironmentalDamage(event);

        verify(event, never()).setCancelled(true);
        assertEquals(EntityDamageEvent.DamageCause.POISON.name(), onlyWalk(hurt).damageCauseName());
    }

    /** The trigger ids of exactly {@code expected} captured walks, in the order they ran. */
    private List<Integer> walkedTriggers(int expected) {
        ArgumentCaptor<Activation> activations = ArgumentCaptor.forClass(Activation.class);
        verify(executor, times(expected)).run(any(), any(), activations.capture(), any(), any(), any());
        return activations.getAllValues().stream().map(Activation::triggerId).toList();
    }

    /** The single captured walk's context, asserting it was {@code triggerId}'s and the only one. */
    private ActivationContext onlyWalk(int triggerId) {
        ArgumentCaptor<Activation> activations = ArgumentCaptor.forClass(Activation.class);
        ArgumentCaptor<ActivationContext> contexts = ArgumentCaptor.forClass(ActivationContext.class);
        verify(executor, times(1)).run(any(), any(), activations.capture(), contexts.capture(), any(), any());
        assertEquals(triggerId, activations.getValue().triggerId());
        return contexts.getValue();
    }

    private EntityDamageEvent damage(EntityDamageEvent.DamageCause cause, double base, double vanillaFinal) {
        Player player = player();
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getCause()).thenReturn(cause);
        when(event.getDamage()).thenReturn(base);
        when(event.getFinalDamage()).thenReturn(vanillaFinal);
        return event;
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ACTOR);
        Location at = mock(Location.class);
        when(at.getWorld()).thenReturn(mock(World.class));
        when(player.getLocation()).thenReturn(at);
        return player;
    }
}
