package engine.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.model.Ability;
import compile.model.CompiledCondition;
import compile.model.cond.Cond;
import engine.interact.SoulSpender;
import engine.interact.SuppressionSet;
import engine.stores.CooldownStore;
import engine.stores.SuppressionStore;
import schema.diag.Source;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import testfx.Abilities;

class ActivationPipelineTest {

    private static final UUID ACTOR = UUID.randomUUID();

    private final CooldownStore cooldowns = new CooldownStore();
    private final FakeSpender spender = new FakeSpender();
    private final ActivationPipeline pipeline = new ActivationPipeline(cooldowns, spender);

    /** A stand-in for the player's cross-gem soul pool: spends from a single settable balance. */
    private static final class FakeSpender implements SoulSpender {
        private int balance;
        @Override public boolean trySpend(UUID player, int cost) {
            if (balance < cost) {
                return false;
            }
            balance -= cost;
            return true;
        }
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
            return Abilities.ability().triggerMask(triggerMask).level(level).chance(baseChance)
                    .cooldown(cooldownTicks).soulCost(soulCost).worldBlacklist(worldBlacklist)
                    .condition(condition).cooldownScope(cdEnchant, cdGroup, cdType).suppressKey(suppressKey)
                    .build();
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
        ActivationPipeline p = new ActivationPipeline(cooldowns, spender, store,
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
        ActivationPipeline p = new ActivationPipeline(cooldowns, spender, store,
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
        ActivationPipeline guarded = new ActivationPipeline(cooldowns, spender,
                (ab, act) -> false, ActivationPipeline.Guard.ALLOW);
        assertEquals(GateOutcome.BLOCKED_PROTECTION, guarded.evaluate(new Ab().build(), act().build()));
    }

    @Test
    void preActivateGuardCancels() {
        ActivationPipeline guarded = new ActivationPipeline(cooldowns, spender,
                ActivationPipeline.Guard.ALLOW, (ab, act) -> false);
        assertEquals(GateOutcome.CANCELLED, guarded.evaluate(new Ab().build(), act().build()));
    }

    @Test
    void soulCostConsumedWhenInSoulModeAndAffordable() {
        Ab a = new Ab();
        a.soulCost = 3;
        spender.balance = 10;
        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(a.build(), act().soulMode(ACTOR).build()));
        assertEquals(7, spender.balance);
    }

    @Test
    void soulCostInsufficientFailsAndLeavesBalance() {
        Ab a = new Ab();
        a.soulCost = 3;
        spender.balance = 2;
        assertEquals(GateOutcome.NO_SOULS, pipeline.evaluate(a.build(), act().soulMode(ACTOR).build()));
        assertEquals(2, spender.balance);
    }

    @Test
    void soulCostAbilityNeverFiresOutsideSoulMode() {
        Ab a = new Ab();
        a.soulCost = 3;
        // §J no active gem → a soul-cost ability is BLOCKED (NO_SOULS), never fired for free (the fixed bug).
        assertEquals(GateOutcome.NO_SOULS, pipeline.evaluate(a.build(), act().build()));
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

        spender.balance = 10;
        assertEquals(GateOutcome.ON_COOLDOWN, pipeline.evaluate(a.build(), act().soulMode(ACTOR).build()));
        assertEquals(10, spender.balance); // souls untouched — gate 6 stopped before gate 10
    }

    @Test
    void soulsNotSpentWhenChanceFails() {
        Ab a = new Ab();
        a.baseChance = 0.0; // chance gate (8) fails before the soul gate (10)
        a.soulCost = 5;
        spender.balance = 10;
        assertEquals(GateOutcome.CHANCE_FAILED, pipeline.evaluate(a.build(), act().soulMode(ACTOR).build()));
        assertEquals(10, spender.balance);
    }

    @Test
    void gate6ReservationSerializesConcurrentHitsAndRollsBackOnCancel() throws Exception {
        // F05: gate 6 atomically RESERVES the cooldown, so a same-tick concurrent hit on the same key can't also
        // pass. Park T1 inside the PreActivate guard (past gate 6, reservation held), fire a second evaluate on the
        // main thread, then have T1's guard deny — the rollback must leave the cooldown ready for a third hit.
        Ab a = new Ab();
        a.cdEnchant = 1;
        a.cooldownTicks = 40;

        CountDownLatch parked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger guardCalls = new AtomicInteger();
        ActivationPipeline.Guard preActivate = (ab, ctx) -> {
            if (guardCalls.getAndIncrement() == 0) {
                parked.countDown();                     // T1 has reserved the cooldown at gate 6
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return false;                           // deny → CANCELLED, which must roll the reservation back
            }
            return true;                                // later hits allow
        };
        ActivationPipeline p = new ActivationPipeline(cooldowns, spender,
                ActivationPipeline.Guard.ALLOW, preActivate);

        AtomicReference<GateOutcome> t1 = new AtomicReference<>();
        Thread worker = new Thread(() -> t1.set(p.evaluate(a.build(), act().build())));
        worker.start();
        assertTrue(parked.await(2, TimeUnit.SECONDS));

        // The concurrent hit sees T1's live reservation and is turned away at gate 6 (never reaching the guard).
        assertEquals(GateOutcome.ON_COOLDOWN, p.evaluate(a.build(), act().build()));

        release.countDown();
        worker.join(2000);
        assertEquals(GateOutcome.CANCELLED, t1.get());

        // Rollback proven: with the reservation released, a fresh hit now activates.
        assertEquals(GateOutcome.ACTIVATED, p.evaluate(a.build(), act().build()));
        assertEquals(2, guardCalls.get()); // T1 + the third hit; the ON_COOLDOWN hit stopped at gate 6
    }

    @Test
    void chanceFailRollsBackSoARetrySameTickActivates() {
        // A chance-fail must NOT arm the cooldown — the gate-6 reservation is released on the fail path.
        Ab a = new Ab();
        a.cdEnchant = 3;
        a.cooldownTicks = 40;
        a.baseChance = 50.0;
        assertEquals(GateOutcome.CHANCE_FAILED, pipeline.evaluate(a.build(), act().chanceRoll(() -> 75.0).build()));
        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(a.build(), act().chanceRoll(() -> 25.0).build()));
    }

    @Test
    void useChanceFailArmsCooldownSoSpamCantRetryForFree() {
        // F16 use path: with spendCooldownOnChanceFail=true a failed roll KEEPS the gate-6 reservation, so the
        // failed attempt spends the cooldown — a spammed sub-100% use-item is throttled one attempt per window.
        Ab a = new Ab();
        a.cdEnchant = 7;
        a.cooldownTicks = 40;
        a.baseChance = 50.0;
        assertEquals(GateOutcome.CHANCE_FAILED,
                pipeline.evaluate(a.build(), act().chanceRoll(() -> 75.0).build(), true)); // fail arms the cooldown
        assertEquals(GateOutcome.ON_COOLDOWN,
                pipeline.evaluate(a.build(), act().chanceRoll(() -> 25.0).build(), true)); // same tick → blocked
        assertEquals(GateOutcome.ACTIVATED,
                pipeline.evaluate(a.build(), Activation.builder(ACTOR, 3, 0, 140L).chanceRoll(() -> 25.0).build(), true));
    }

    @Test
    void hotChanceFailStillReleasesCooldownSoProcEnchantsRollEveryHit() {
        // F16 regression: the hot path (2-arg / false) must keep releasing on a chance fail, or a sub-100% proc
        // enchant with a cooldown would almost never fire.
        Ab a = new Ab();
        a.cdEnchant = 8;
        a.cooldownTicks = 40;
        a.baseChance = 50.0;
        assertEquals(GateOutcome.CHANCE_FAILED,
                pipeline.evaluate(a.build(), act().chanceRoll(() -> 75.0).build(), false));
        assertEquals(GateOutcome.ACTIVATED, // same tick, no cooldown accrued
                pipeline.evaluate(a.build(), act().chanceRoll(() -> 25.0).build(), false));
    }

    @Test
    void partialReservationRollsBackTheAcquiredScopeWhenALaterScopeIsBlocked() {
        // enchant scope ready, group scope pre-armed: gate 6 acquires enchant, finds group blocked, and must
        // roll the enchant reservation back so another ability sharing that enchant scope stays free.
        Ab a = new Ab();
        a.cdEnchant = 1;
        a.cdGroup = 2;
        a.cooldownTicks = 40;
        cooldowns.arm(ACTOR, CooldownStore.key(1, 2, 0), 100L, 40); // group scope pre-armed by "another ability"

        assertEquals(GateOutcome.ON_COOLDOWN, pipeline.evaluate(a.build(), act().build()));
        assertTrue(cooldowns.ready(ACTOR, CooldownStore.key(0, 1, 0), 100L),
                "the enchant scope's brief reservation must be rolled back");
    }
}
