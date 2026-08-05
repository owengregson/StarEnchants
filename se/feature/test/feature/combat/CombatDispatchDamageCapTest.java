package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
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
import engine.stores.DamageCapStore;
import engine.stores.SuppressionStore;
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
import platform.text.Numbers;
import schema.spec.Args;
import testfx.Abilities;
import testfx.Envs;
import testfx.RecordingSchedulerBackend;
import testfx.Snapshots;
import testfx.SyncSchedulerBackend;
import testfx.WornStates;

/**
 * {@code DAMAGE_CAP} (ADR-0049 Diminish) at the {@link CombatDispatch} seam — the ORDER of arm vs consume, which
 * is the whole mechanic and which nothing below this layer can prove: the effect arms the store INLINE mid-walk,
 * so only the dispatch decides whether the window the walk just opened belongs to this hit or the next one.
 *
 * <p>Everything below the dispatch is REAL — the production effect/selector registries, the real pipeline, a real
 * sink and the real store — because the defect being pinned was a pure sequencing bug between them: the consume
 * used to sit BELOW the defence walk, so a cap was spent by the very hit that armed it, {@code duration:} never
 * mattered, and the advertised "cap your NEXT incoming hit" never happened.
 */
class CombatDispatchDamageCapTest {

    private static final int ATTACK_TRIGGER = 0;
    private static final int DEFENSE_TRIGGER = 1;
    /** Deliberately NOT the spec's 100-tick default: a drifted {@code duration} key would fall back and show up here. */
    private static final int WINDOW_TICKS = 40;
    private static final double FACTOR = 0.5;

    private final UUID attackerId = UUID.randomUUID();
    private final UUID victimId = UUID.randomUUID();
    private final long[] tick = {0L};

    private SinkEnv env;

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
     * A dispatch whose VICTIM carries one DEFENSE ability — {@code DAMAGE_CAP factor 0.5} over a
     * {@value #WINDOW_TICKS}-tick window at chance 100 — built through the real compiled-effect shape so the
     * production kind arms it. {@code armed = false} leaves the victim bare, isolating the consume side.
     */
    private CombatDispatch dispatch(boolean armed, boolean reflect) {
        return dispatch(armed, reflect, "");
    }

    private CombatDispatch dispatch(boolean armed, boolean reflect, String feedback) {
        RuntimeHandles handles = new RuntimeHandles(new RegistryResolvers());
        env = Envs.sink().nowTicks(() -> tick[0]).build();
        ModernDispatchSink sink = new ModernDispatchSink(handles, env);
        SinkFactory sinkFactory = mock(SinkFactory.class);
        when(sinkFactory.create(any())).thenReturn(sink);

        CompiledEffect cap = new CompiledEffect("DAMAGE_CAP",
                Args.empty().with("factor", FACTOR).with("reflect", reflect)
                        .with("duration", WINDOW_TICKS).with("feedback", feedback),
                CompiledSelector.SELF, 0, Affinity.CONTEXT_LOCAL);
        Ability capAbility = Abilities.ability().id(0).level(1).trigger(DEFENSE_TRIGGER).effects(cap).build();
        Snapshot snapshot = Snapshots.snapshot()
                .abilities(capAbility)
                .stableKeys("enchants/diminish/1")
                .build();
        ContentHolder content = mock(ContentHolder.class);
        when(content.snapshot()).thenReturn(snapshot);

        AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE, new SuppressionStore(),
                        ActivationPipeline.Guard.ALLOW, ReboundGate.INSTANCE),
                AreaScan.NONE);
        WornStateStore worn = mock(WornStateStore.class);
        if (armed) {
            when(worn.get(victimId)).thenReturn(WornStates.worn().byTrigger(DEFENSE_TRIGGER, 0).build());
        }

        return new CombatDispatch(executor, sinkFactory, mock(ActorProbe.class), content, worn,
                ATTACK_TRIGGER, DEFENSE_TRIGGER, -1, -1, p -> Optional.empty(), env,
                CombatDispatch.Caps.unlimited(), new ModernProjectiles());
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

    /** Land one hit of {@code damage} and return what the dispatch committed to the event. */
    private double strike(CombatDispatch dispatch, Player attacker, Player victim, double damage) {
        double[] committed = {Double.NaN};
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(attacker);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamage()).thenReturn(damage);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        doAnswer(inv -> {
            committed[0] = inv.getArgument(0);
            return null;
        }).when(event).setDamage(anyDouble());
        dispatch.onDamage(event);
        return committed[0];
    }

    @Test
    void theArmingHitDoesNotSpendItsOwnWindow() {
        CombatDispatch dispatch = dispatch(true, false);

        double committed = strike(dispatch, player(attackerId), victim(), 10.0);

        assertEquals(10.0, committed, 1e-9,
                "the hit that ARMS the cap must land in full — spending it here is the defect");
        DamageCapStore.Cap survived = env.stores().damageCap().consumeArmed(victimId, tick[0]);
        assertNotNull(survived, "the window has to outlive the hit that opened it, or duration: means nothing");
        assertEquals(FACTOR * 10.0, survived.value(), 1e-9);
    }

    @Test
    void theNextHitInsideTheWindowIsCapped() {
        CombatDispatch dispatch = dispatch(true, false);
        Player attacker = player(attackerId);
        Player victim = victim();

        assertEquals(10.0, strike(dispatch, attacker, victim, 10.0), 1e-9, "hit 1 arms 0.5 × 10.0 = 5.0");
        tick[0] = WINDOW_TICKS - 1; // still inside the armed window

        assertEquals(FACTOR * 10.0, strike(dispatch, attacker, victim, 10.0), 1e-9,
                "hit 2 is the NEXT incoming hit the enchant advertises — it takes the cap");
    }

    @Test
    void anArmedCapIsSpentByExactlyOneHit() {
        CombatDispatch dispatch = dispatch(false, false); // bare victim: nothing re-arms behind the consume
        env.stores().damageCap().arm(victimId, 5.0, false, 0L, WINDOW_TICKS);
        Player attacker = player(attackerId);
        Player victim = victim();
        tick[0] = 10L;

        assertEquals(5.0, strike(dispatch, attacker, victim, 10.0), 1e-9, "the armed cap bites once");
        tick[0] = 11L;

        assertEquals(10.0, strike(dispatch, attacker, victim, 10.0), 1e-9,
                "one-shot: the window is gone even though its duration had not elapsed");
    }

    @Test
    void aWindowThatElapsesBeforeTheNextHitDoesNotBite() {
        CombatDispatch dispatch = dispatch(true, false);
        Player attacker = player(attackerId);
        Player victim = victim();

        assertEquals(10.0, strike(dispatch, attacker, victim, 10.0), 1e-9, "hit 1 arms 5.0 for 40 ticks");
        tick[0] = WINDOW_TICKS; // the expiry tick itself counts as elapsed (half-open)

        assertEquals(10.0, strike(dispatch, attacker, victim, 10.0), 1e-9,
                "the window closed untaken — the hit lands in full");
    }

    @Test
    void theOverflowAboveTheCapGoesBackAtTheAttacker() {
        CombatDispatch dispatch = dispatch(false, true); // bare victim; the armed cap carries the reflect flag
        env.stores().damageCap().arm(victimId, 4.0, true, 0L, WINDOW_TICKS);
        Player attacker = player(attackerId);
        Player victim = victim();
        tick[0] = 10L;

        assertEquals(4.0, strike(dispatch, attacker, victim, 10.0), 1e-9);

        verify(attacker).damage(6.0, victim); // §5 Vengeful Diminish: 10.0 − 4.0, victim-attributed
    }

    @Test
    void aCapUnderTheIncomingHitLeavesItAloneAndStillSpends() {
        CombatDispatch dispatch = dispatch(false, true);
        env.stores().damageCap().arm(victimId, 12.0, true, 0L, WINDOW_TICKS); // a ceiling above this hit
        Player attacker = player(attackerId);
        Player victim = victim();
        tick[0] = 10L;

        assertEquals(10.0, strike(dispatch, attacker, victim, 10.0), 1e-9, "a cap only clamps what exceeds it");
        verify(attacker, never()).damage(anyDouble(), any()); // no overflow, so nothing to reflect
        assertNull(env.stores().damageCap().consumeArmed(victimId, tick[0]),
                "consumed even where it did not bite — a one-shot is spent by the hit it covers");
    }

    /**
     * R-QC19: a cap armed on hit N is priced off hit N's OWN committed damage. The arm is recorded pending
     * inside the defence walk and priced at the fold commit below it, so "cap your next hit at half the hit
     * that armed it" names the swing the player just felt — not, as this pinned test used to document, the
     * unrelated one before it. That one-hit lag was what made Vengeful Diminish's advertised double halving
     * (−50 % on the arming hit, then half of it again) untrue in play.
     */
    @Test
    void theArmedCapIsPricedOffTheArmingHit() {
        CombatDispatch dispatch = dispatch(true, false);
        Player attacker = player(attackerId);
        Player victim = victim();

        // Deliberately different magnitudes: under the old lagged basis hit 3 would be capped at 0.5 × 8.0.
        assertEquals(8.0, strike(dispatch, attacker, victim, 8.0), 1e-9,
                "hit 1 arms 0.5 × 8.0 and lands in full — an arm never spends its own window");
        tick[0] = WINDOW_TICKS - 1;
        assertEquals(FACTOR * 8.0, strike(dispatch, attacker, victim, 20.0), 1e-9,
                "hit 2 takes hit 1's cap, 4.0 — and re-arms off its OWN committed 4.0");
        tick[0] = WINDOW_TICKS;

        assertEquals(FACTOR * FACTOR * 8.0, strike(dispatch, attacker, victim, 20.0), 1e-9,
                "2.0 — half of what hit 2 actually committed, which is the hit that armed this");
    }

    /**
     * The arming line is announced at the COMMIT, not the arm, because that is the first moment its {damage}
     * token has a true value to carry (R-QC19). Nothing below the dispatch can prove it: the sink records a
     * factor, and only the fold knows what the hit cost.
     */
    @Test
    void theArmingLineReportsThePricedCap() {
        CombatDispatch dispatch = dispatch(true, false, "DIMINISH {damage}");
        Player victim = victim();

        strike(dispatch, player(attackerId), victim, 9.0);

        verify(victim).sendMessage("DIMINISH " + Numbers.chat(FACTOR * 9.0));
    }

    /** A proc that arms nothing claims nothing: a 0-damage hit prices a 0 cap, and no line goes out for it. */
    @Test
    void anArmThatPricesNothingStaysSilent() {
        CombatDispatch dispatch = dispatch(true, false, "DIMINISH {damage}");
        Player victim = victim();

        strike(dispatch, player(attackerId), victim, 0.0);

        assertNull(env.stores().damageCap().consumeArmed(victimId, tick[0]));
        verify(victim, never()).sendMessage(anyString());
    }
}
