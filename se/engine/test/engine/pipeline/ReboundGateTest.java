package engine.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import compile.model.Ability;
import compile.model.ScopeKinds;
import engine.interact.ReboundPlan;
import engine.interact.SoulSpender;
import engine.stores.CooldownStore;
import engine.stores.ReboundStore;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import testfx.Abilities;

/**
 * The production gate-9 wiring (PROC_REBOUND). {@link ActivationPipelineTest} already pins what a DENYING
 * gate-9 guard does; what only this can prove is that {@link ReboundGate#INSTANCE} denies exactly when the
 * activation's plan claims — and that a claimed ability therefore leaves no cooldown behind on the attacker,
 * whose enchant did not get to run.
 */
class ReboundGateTest {

    private static final int TRIGGER = 0;
    private static final int SCOPE = 1;
    private static final int COOLDOWN_TICKS = 40;
    private static final UUID ATTACKER = UUID.randomUUID();
    private static final UUID REFLECTOR = UUID.randomUUID();

    private final CooldownStore cooldowns = new CooldownStore();
    private final ActivationPipeline pipeline = new ActivationPipeline(cooldowns, SoulSpender.NONE,
            new engine.stores.SuppressionStore(), ActivationPipeline.Guard.ALLOW, ReboundGate.INSTANCE);

    private static Ability incoming() {
        return Abilities.ability().id(3).level(4).trigger(TRIGGER)
                .cooldown(COOLDOWN_TICKS).cooldownScope(SCOPE, -1, -1).build();
    }

    private Activation.Builder act() {
        return Activation.builder(ATTACKER, -1, TRIGGER, 0L);
    }

    /** A plan over a store armed to answer tier 6 at level 4 or below, always rolling in. */
    private static ReboundPlan planClaiming(int incomingTier) {
        ReboundStore store = new ReboundStore();
        store.arm(REFLECTOR, 9, 4, 100.0, 6, 7);
        return new ReboundPlan(store, REFLECTOR, ability -> incomingTier, () -> 0.0);
    }

    @Test
    void aClaimVetoesTheActivationAndReleasesTheCooldownReservation() {
        ReboundPlan plan = planClaiming(6);

        assertEquals(GateOutcome.CANCELLED, pipeline.evaluate(incoming(), act().rebound(plan).build()));
        assertArrayEquals(new int[] {3}, plan.claimed(), "the vetoed ability is what the swapped run re-executes");
        // The attacker's enchant never ran, so it must not be sitting on a cooldown either.
        assertEquals(0, cooldowns.remainingTicks(ATTACKER, CooldownStore.key(ScopeKinds.ENCHANT, SCOPE, 0), 0L));
    }

    @Test
    void aPlanThatDoesNotClaimLetsTheActivationThrough() {
        ReboundPlan plan = planClaiming(8); // out of the armed band

        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(incoming(), act().rebound(plan).build()));
        assertArrayEquals(new int[0], plan.claimed());
    }

    @Test
    void noPlanIsTheEveryOtherActivationPath() {
        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(incoming(), act().build()));
    }
}
