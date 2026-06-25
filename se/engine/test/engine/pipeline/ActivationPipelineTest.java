package engine.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.model.Ability;
import compile.model.Affinity;
import compile.model.CompiledCondition;
import compile.model.CompiledEffect;
import compile.model.SourceKind;
import compile.model.cond.Cond;
import engine.interact.SoulLedger;
import engine.interact.SuppressionSet;
import engine.stores.CooldownStore;
import engine.stores.SuppressionStore;
import schema.diag.Source;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActivationPipelineTest {

    private static final UUID ACTOR = UUID.randomUUID();

    private final CooldownStore cooldowns = new CooldownStore();
    private final SoulLedger souls = new SoulLedger();
    private final ActivationPipeline pipeline = new ActivationPipeline(cooldowns, souls);

    private static final class IntBalance implements SoulLedger.Balance {
        private int souls;
        IntBalance(int souls) { this.souls = souls; }
        public int souls() { return souls; }
        public void setSouls(int souls) { this.souls = souls; }
    }

    /** Mutable builder defaulting to an always-fires-on-trigger-0 ability; each test tweaks one field. */
    private static final class Ab {
        int triggerMask = 1 << 0;
        int level = 1;
        double baseChance = 100.0;
        int cooldownTicks = 0;
        int soulCost = 0;
        long worldBlacklist = 0L;
        CompiledCondition condition = null;
        int cdEnchant = -1, cdGroup = -1, cdType = -1;
        int suppressKey = -1;

        Ability build() {
            return new Ability(0, 0, SourceKind.ENCHANT, triggerMask, level, baseChance,
                    cooldownTicks, soulCost, worldBlacklist, condition, new CompiledEffect[0],
                    0, Affinity.CONTEXT_LOCAL, cdEnchant, cdGroup, cdType, suppressKey, 0);
        }
    }

    private static Activation.Builder act() {
        return Activation.builder(ACTOR, 3, 0, 100L); // world 3, trigger 0, tick 100
    }

    @Test
    void allGatesPassActivates() {
        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(new Ab().build(), act().build()));
    }

    @Test
    void blockedWorld() {
        Ab a = new Ab();
        a.worldBlacklist = 1L << 3; // world 3 blacklisted
        assertEquals(GateOutcome.BLOCKED_WORLD, pipeline.evaluate(a.build(), act().build()));
        assertEquals(GateOutcome.ACTIVATED,
                pipeline.evaluate(a.build(), Activation.builder(ACTOR, 5, 0, 100L).build()));
    }

    @Test
    void wrongTrigger() {
        Activation onTrigger1 = Activation.builder(ACTOR, 3, 1, 100L).build();
        assertEquals(GateOutcome.WRONG_TRIGGER, pipeline.evaluate(new Ab().build(), onTrigger1));
    }

    @Test
    void suppressed() {
        Ab a = new Ab();
        a.suppressKey = 7;
        SuppressionSet sup = new SuppressionSet();
        sup.add(7);
        assertEquals(GateOutcome.SUPPRESSED, pipeline.evaluate(a.build(), act().suppression(sup).build()));
    }

    @Test
    void timedSuppressionGatesTheMatchingScopeThenClearsAtExpiry() {
        SuppressionStore store = new SuppressionStore();
        ActivationPipeline p = new ActivationPipeline(cooldowns, souls, store,
                ActivationPipeline.Guard.ALLOW, ActivationPipeline.Guard.ALLOW);
        Ab a = new Ab();
        a.cdGroup = 5; // this ability is in group-scope id 5
        store.suppress(ACTOR, CooldownStore.key(1, 5), 90L, 40); // DISABLE_GROUP id 5: suppressed [90,130)

        assertEquals(GateOutcome.SUPPRESSED, p.evaluate(a.build(), act().build())); // tick 100 → suppressed
        assertEquals(GateOutcome.ACTIVATED,
                p.evaluate(a.build(), Activation.builder(ACTOR, 3, 0, 140L).build())); // tick 140 → elapsed
    }

    @Test
    void suppressionOnlyGatesTheMatchingScopeKindAndId() {
        SuppressionStore store = new SuppressionStore();
        ActivationPipeline p = new ActivationPipeline(cooldowns, souls, store,
                ActivationPipeline.Guard.ALLOW, ActivationPipeline.Guard.ALLOW);
        Ab a = new Ab();
        a.cdEnchant = 5; // ability is in ENCHANT-scope id 5
        store.suppress(ACTOR, CooldownStore.key(1, 5), 0L, 100); // a GROUP-scope id 5 suppression
        assertEquals(GateOutcome.ACTIVATED, p.evaluate(a.build(), act().build())); // different kind → not matched
        store.suppress(ACTOR, CooldownStore.key(0, 6), 0L, 100); // ENCHANT id 6 (different id)
        assertEquals(GateOutcome.ACTIVATED, p.evaluate(a.build(), act().build())); // different id → not matched
    }

    @Test
    void onCooldownThenReadyAfterExpiry() {
        Ab a = new Ab();
        a.cdEnchant = 5;
        a.cooldownTicks = 40;
        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(a.build(), act().build()));
        assertEquals(GateOutcome.ON_COOLDOWN, pipeline.evaluate(a.build(), act().build()));
        assertEquals(GateOutcome.ACTIVATED,
                pipeline.evaluate(a.build(), Activation.builder(ACTOR, 3, 0, 140L).build()));
    }

    @Test
    void conditionStopFails() {
        Ab a = new Ab();
        a.condition = CompiledCondition.gate(new Cond.BoolLit(false), Source.UNKNOWN);
        assertEquals(GateOutcome.CONDITION_FAILED, pipeline.evaluate(a.build(), act().build()));
    }

    @Test
    void chanceRollGate() {
        Ab a = new Ab();
        a.baseChance = 50.0;
        assertEquals(GateOutcome.CHANCE_FAILED, pipeline.evaluate(a.build(), act().chanceRoll(() -> 75.0).build()));
        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(a.build(), act().chanceRoll(() -> 25.0).build()));
    }

    @Test
    void protectionGuardBlocks() {
        ActivationPipeline guarded = new ActivationPipeline(cooldowns, souls,
                (ab, act) -> false, ActivationPipeline.Guard.ALLOW);
        assertEquals(GateOutcome.BLOCKED_PROTECTION, guarded.evaluate(new Ab().build(), act().build()));
    }

    @Test
    void preActivateGuardCancels() {
        ActivationPipeline guarded = new ActivationPipeline(cooldowns, souls,
                ActivationPipeline.Guard.ALLOW, (ab, act) -> false);
        assertEquals(GateOutcome.CANCELLED, guarded.evaluate(new Ab().build(), act().build()));
    }

    @Test
    void soulCostConsumedWhenInSoulModeAndAffordable() {
        Ab a = new Ab();
        a.soulCost = 3;
        IntBalance gem = new IntBalance(10);
        assertEquals(GateOutcome.ACTIVATED,
                pipeline.evaluate(a.build(), act().soulMode(UUID.randomUUID(), gem).build()));
        assertEquals(7, gem.souls());
    }

    @Test
    void soulCostInsufficientFailsAndLeavesBalance() {
        Ab a = new Ab();
        a.soulCost = 3;
        IntBalance gem = new IntBalance(2);
        assertEquals(GateOutcome.NO_SOULS,
                pipeline.evaluate(a.build(), act().soulMode(UUID.randomUUID(), gem).build()));
        assertEquals(2, gem.souls());
    }

    @Test
    void soulCostSkippedWhenNotInSoulMode() {
        Ab a = new Ab();
        a.soulCost = 3;
        // no soulMode() → gate 10 does not apply, ability still fires (§3.3 "only if gem active")
        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(a.build(), act().build()));
    }

    @Test
    void cooldownIsArmedOnlyOnActivation() {
        Ab a = new Ab();
        a.cdEnchant = 1;
        a.cdGroup = 2;
        a.cooldownTicks = 40;
        pipeline.evaluate(a.build(), act().build()); // ACTIVATED → arms both scopes
        assertFalse(cooldowns.ready(ACTOR, CooldownStore.key(0, 1), 100L)); // enchant scope armed
        assertFalse(cooldowns.ready(ACTOR, CooldownStore.key(1, 2), 100L)); // group scope armed
        assertTrue(cooldowns.ready(ACTOR, CooldownStore.key(0, 1), 140L));  // ready after expiry
    }

    @Test
    void earlierGateWinsAndHasNoLaterSideEffects() {
        // On cooldown AND would fail chance AND has a soul cost: the cooldown gate (6) wins,
        // and neither souls (10) nor a re-arm (11) happen.
        Ab a = new Ab();
        a.cdEnchant = 9;
        a.cooldownTicks = 40;
        a.baseChance = 0.0;   // would fail the chance roll if reached
        a.soulCost = 5;       // would be spent if reached
        cooldowns.arm(ACTOR, CooldownStore.key(0, 9), 100L, 40); // pre-armed

        IntBalance gem = new IntBalance(10);
        assertEquals(GateOutcome.ON_COOLDOWN,
                pipeline.evaluate(a.build(), act().soulMode(UUID.randomUUID(), gem).build()));
        assertEquals(10, gem.souls()); // souls untouched — gate 6 stopped before gate 10
    }

    @Test
    void soulsNotSpentWhenChanceFails() {
        Ab a = new Ab();
        a.baseChance = 0.0; // chance gate (8) fails before the soul gate (10)
        a.soulCost = 5;
        IntBalance gem = new IntBalance(10);
        assertEquals(GateOutcome.CHANCE_FAILED,
                pipeline.evaluate(a.build(), act().soulMode(UUID.randomUUID(), gem).build()));
        assertEquals(10, gem.souls());
    }
}
