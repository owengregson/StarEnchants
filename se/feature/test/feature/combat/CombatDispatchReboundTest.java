package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.load.ContentHolder;
import compile.model.Ability;
import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.model.CompiledSelector;
import compile.model.Snapshot;
import engine.effect.kind.BuiltinEffects;
import engine.interact.SoulSpender;
import engine.pipeline.ActivationPipeline;
import engine.pipeline.ReboundGate;
import engine.run.AbilityExecutor;
import engine.run.ActorProbe;
import engine.run.AreaScan;
import engine.selector.kind.BuiltinSelectors;
import engine.sink.ModernDispatchSink;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.stores.CooldownStore;
import feature.compat.ModernProjectiles;
import item.worn.WornStateStore;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import schema.spec.Args;
import testfx.Abilities;
import testfx.Envs;
import testfx.RecordingSchedulerBackend;
import testfx.Snapshots;
import testfx.SyncSchedulerBackend;
import testfx.WornStates;

/**
 * PROC_REBOUND end to end at the dispatch seam — the blocker the two earlier attempts stopped on. One
 * {@code DAMAGE_MOD} on the attacker's ATTACK walk; when the victim's armed grade claims it, the attacker's
 * enchant must NOT price the hit the victim takes (that would be the exact inversion of the intent), and its
 * marginal damage must instead come back at the attacker through the rebound-direction fold.
 *
 * <p>Everything below the dispatch is REAL — the production effect/selector registries, the real pipeline
 * with the production gate-9 guard, and a real sink — because the whole failure mode lives in which
 * accumulator the contribution lands in.
 */
class CombatDispatchReboundTest {

    private static final int ATTACK_TRIGGER = 0;
    private static final int DEFENSE_TRIGGER = 1;
    private static final int REBOUND_DEF = 7;
    private static final int INCOMING_TIER = 6;

    private final UUID attackerId = UUID.randomUUID();
    private final UUID victimId = UUID.randomUUID();

    private SinkEnv env;
    private ModernDispatchSink sink;

    @BeforeEach
    void setUp() {
        Scheduling.install(new RecordingSchedulerBackend());
    }

    @AfterEach
    void reset() {
        ReHitGuard.clearSkipped();
        Scheduling.install(new SyncSchedulerBackend());
    }

    /**
     * A dispatch whose attacker carries one ATTACK ability: {@code DAMAGE_MOD attack/add/+50%} at level 4,
     * built through the real compiled-effect shape so the production kind runs it.
     */
    private CombatDispatch dispatch(int tierWeight, double attackScale) {
        RuntimeHandles handles = new RuntimeHandles(new RegistryResolvers());
        env = Envs.sink().build();
        sink = new ModernDispatchSink(handles, env);
        SinkFactory sinkFactory = mock(SinkFactory.class);
        when(sinkFactory.create(any())).thenReturn(sink);

        CompiledEffect boost = new CompiledEffect("DAMAGE_MOD",
                Args.empty().with("side", "attack").with("mode", "add").with("amount", 50.0),
                CompiledSelector.SELF, 0, Affinity.CONTEXT_LOCAL);
        Ability attackAbility = Abilities.ability().id(0).level(4).trigger(ATTACK_TRIGGER)
                .effects(boost).build();
        Snapshot snapshot = Snapshots.snapshot()
                .abilities(attackAbility)
                .stableKeys("enchants/probe/4")
                .build();
        ContentHolder content = mock(ContentHolder.class);
        when(content.snapshot()).thenReturn(snapshot);

        AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE, new engine.stores.SuppressionStore(),
                        ActivationPipeline.Guard.ALLOW, ReboundGate.INSTANCE),
                AreaScan.NONE);
        WornStateStore worn = mock(WornStateStore.class);
        when(worn.get(attackerId)).thenReturn(WornStates.worn().byTrigger(ATTACK_TRIGGER, 0).build());

        CombatDispatch dispatch = new CombatDispatch(executor, sinkFactory, mock(ActorProbe.class), content, worn,
                ATTACK_TRIGGER, DEFENSE_TRIGGER, -1, -1, p -> Optional.empty(), env,
                new CombatDispatch.Caps(() -> -1.0, () -> -1.0, () -> attackScale, () -> true, () -> true),
                new ModernProjectiles());
        dispatch.bindTiers(ability -> tierWeight);
        return dispatch;
    }

    private Player player(UUID id) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(id);
        when(p.isValid()).thenReturn(true);
        when(p.getLocation()).thenReturn(mock(Location.class));
        return p;
    }

    private Player victim() {
        Player victim = player(victimId);
        when(victim.getNoDamageTicks()).thenReturn(0);
        when(victim.getMaximumNoDamageTicks()).thenReturn(20);
        return victim;
    }

    private EntityDamageByEntityEvent hit(Player attacker, Player victim, double[] committed) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(attacker);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamage()).thenReturn(10.0);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        doAnswer(inv -> {
            committed[0] = inv.getArgument(0);
            return null;
        }).when(event).setDamage(anyDouble());
        return event;
    }

    /** Arm the victim's grade at 100% so the claim is deterministic. */
    private void armVictim(int level, int tierMin, int tierMax) {
        env.stores().rebounds().arm(victimId, REBOUND_DEF, level, 100.0, tierMin, tierMax);
    }

    @Test
    void aClaimedProcLeavesTheIncomingHitAloneAndComesBackAtTheAttacker() {
        CombatDispatch dispatch = dispatch(INCOMING_TIER, 1.0);
        armVictim(4, 6, 7);
        Player attacker = player(attackerId);
        Player victim = victim();
        double[] committed = {Double.NaN};

        dispatch.onDamage(hit(attacker, victim, committed));

        assertEquals(10.0, committed[0], 1e-9,
                "the claimed +50% must NOT price the hit the victim is taking — that is the inversion");
        // The marginal damage lands on the attacker instead, as a bounded second application.
        verify(attacker).damage(5.0, victim);
    }

    @Test
    void anUnclaimedProcPricesTheIncomingHitExactlyAsBefore() {
        CombatDispatch dispatch = dispatch(INCOMING_TIER, 1.0);
        // Nothing armed on the victim: the baseline the rebound must not disturb.
        Player attacker = player(attackerId);
        Player victim = victim();
        double[] committed = {Double.NaN};

        dispatch.onDamage(hit(attacker, victim, committed));

        assertEquals(15.0, committed[0], 1e-9);
        verify(attacker, never()).damage(anyDouble(), any());
    }

    @Test
    void theReboundIsPricedByAttackScaleExactlyAsTheIncomingFoldWouldHaveBeen() {
        CombatDispatch dispatch = dispatch(INCOMING_TIER, 5.0);
        armVictim(4, 6, 7);
        Player attacker = player(attackerId);
        Player victim = victim();
        double[] committed = {Double.NaN};

        dispatch.onDamage(hit(attacker, victim, committed));

        assertEquals(10.0, committed[0], 1e-9);
        // Unclaimed, the same enchant would have committed 10 × (1 + 0.5 × 5) = 35, i.e. +25 over the base.
        verify(attacker).damage(25.0, victim);
    }

    @Test
    void anEnchantOutsideTheArmedBandIsNotClaimed() {
        CombatDispatch dispatch = dispatch(8, 1.0); // a mastery-tier proc against a heroic-grade wearer
        armVictim(4, 6, 7);
        Player attacker = player(attackerId);
        Player victim = victim();
        double[] committed = {Double.NaN};

        dispatch.onDamage(hit(attacker, victim, committed));

        assertEquals(15.0, committed[0], 1e-9, "out of band → the proc lands on its owner's target as usual");
        verify(attacker, never()).damage(anyDouble(), any());
    }

    @Test
    void anEnchantAboveTheReboundLevelIsNotClaimed() {
        CombatDispatch dispatch = dispatch(INCOMING_TIER, 1.0);
        armVictim(3, 6, 7); // rebound level 3 < the incoming enchant's level 4
        Player attacker = player(attackerId);
        Player victim = victim();
        double[] committed = {Double.NaN};

        dispatch.onDamage(hit(attacker, victim, committed));

        assertEquals(15.0, committed[0], 1e-9);
        verify(attacker, never()).damage(anyDouble(), any());
    }
}
