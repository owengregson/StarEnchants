package engine.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.model.Ability;
import compile.model.CompiledCondition;
import compile.model.cond.Cond;
import compile.model.cond.NumExpr;
import engine.condition.FactBuffer;
import engine.interact.SoulSpender;
import engine.interact.SuppressionSet;
import engine.stores.CooldownStore;
import engine.stores.SuppressionStore;
import schema.diag.Source;
import schema.grammar.expr.FlowKind;
import java.util.List;
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

    /**
     * A stand-in for the player's cross-gem soul pool: spends from a single settable balance. The carried
     * path draws on the SAME balance, mirroring production's one-ledger rule, and records that it was the
     * path taken so a row can tell the two apart.
     */
    private static final class FakeSpender implements SoulSpender {
        private int balance;
        private boolean carriedPathUsed;

        @Override public boolean trySpend(UUID player, int cost) {
            if (balance < cost) {
                return false;
            }
            balance -= cost;
            return true;
        }

        @Override public boolean trySpendCarried(UUID player, int cost) {
            carriedPathUsed = true;
            return trySpend(player, cost);
        }
    }

    /** Mutable builder defaulting to an always-fires-on-trigger-0 ability; each test tweaks one field. */
    private static final class Ab {
        int triggerMask = 1 << 0;
        int level = 1;
        double baseChance = 100.0;
        int cooldownTicks = 0;
        int soulCost = 0;
        double soulCostGrowth = 1.0;
        int soulCostCap = 0;
        int soulCostDecayPeriod = 0;
        long worldBlacklist = 0L;
        CompiledCondition condition = null;
        int cdEnchant = -1, cdGroup = -1, cdType = -1;
        int suppressKey = -1;
        NumExpr chanceExpr = null;
        boolean soulCostCarried = false;
        boolean cooldownPerVictim = false;

        Ability build() {
            return Abilities.ability().triggerMask(triggerMask).level(level).chance(baseChance)
                    .chanceExpr(chanceExpr)
                    .cooldown(cooldownTicks).soulCost(soulCost).worldBlacklist(worldBlacklist)
                    .soulCostGrowth(soulCostGrowth).soulCostCap(soulCostCap)
                    .soulCostDecayPeriod(soulCostDecayPeriod)
                    .condition(condition).cooldownScope(cdEnchant, cdGroup, cdType).suppressKey(suppressKey)
                    .soulCostCarried(soulCostCarried)
                    .cooldownPerVictim(cooldownPerVictim)
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
    void aDefenderKeyedWindowGatesWhatIsAimedAtItsHolderNotWhatItsHolderDoes() {
        // The whole point of the incoming direction: the window sits on the VICTIM, and the ACTIVATOR (who
        // carries no window at all) is the one gated. Keyed on the activator instead, this ability would sail
        // through — which is the leak a who=@Attacker SUPPRESS from DEFENSE has always had.
        SuppressionStore store = new SuppressionStore();
        ActivationPipeline p = new ActivationPipeline(cooldowns, spender, store,
                ActivationPipeline.Guard.ALLOW, ActivationPipeline.Guard.ALLOW);
        Ab a = new Ab();
        a.cdGroup = 5;
        UUID defender = UUID.randomUUID();
        store.defend(defender, CooldownStore.key(1, 5), 90L, 40, 100, -1, null);

        assertEquals(GateOutcome.SUPPRESSED, p.evaluate(a.build(), act().victimId(defender).build()));
        assertEquals(GateOutcome.ACTIVATED, p.evaluate(a.build(), act().build()),
                "the same ability aimed at nobody is untouched — the window is not the activator's");
        assertEquals(GateOutcome.ACTIVATED,
                p.evaluate(a.build(), act().victimId(UUID.randomUUID()).build()),
                "and aimed at someone else it is untouched too");
        assertEquals(GateOutcome.ACTIVATED,
                p.evaluate(a.build(), Activation.builder(ACTOR, 3, 0, 140L).victimId(defender).build()),
                "tick 140: the window elapsed");
    }

    @Test
    void aDefenderWindowsChanceIsRolledPerIncomingActivation() {
        // A partial mask is the case an arm-time roll cannot express at all: the same window must let one proc
        // through and stop the next. Both verdicts come from ONE window, so a roll hoisted to the arm fails.
        SuppressionStore store = new SuppressionStore();
        UUID defender = UUID.randomUUID();
        Ab a = new Ab();
        a.cdType = 4;
        ActivationPipeline p = new ActivationPipeline(cooldowns, spender, store,
                ActivationPipeline.Guard.ALLOW, ActivationPipeline.Guard.ALLOW);
        store.defend(defender, CooldownStore.key(2, 4), 0L, 200, 50, -1, null);

        assertEquals(GateOutcome.SUPPRESSED,
                p.evaluate(a.build(), act().victimId(defender).chanceRoll(() -> 10.0).build()),
                "a draw under the window's chance blocks");
        assertEquals(GateOutcome.ACTIVATED,
                p.evaluate(a.build(), act().victimId(defender).chanceRoll(() -> 90.0).build()),
                "a draw at or above it lets the proc through, from the SAME live window");
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

    /**
     * EXPR_CHANCE: an expression-valued {@code chance:} is evaluated at the SAME gate against the already
     * populated fact buffer — the gate order is untouched, only the value being compared changes.
     */
    @Test
    void expressionChanceIsEvaluatedAgainstTheFactBuffer() {
        FactBuffer facts = new FactBuffer(1, 0, 0);
        facts.setNumber(0, 2.0);
        Ab a = new Ab();
        a.baseChance = 0.0; // ignored: the expression is the authority when present
        // min(50, %recentattackers% * 10) -> 20 at 2 attackers
        a.chanceExpr = new NumExpr.Fn(NumExpr.FnKind.MIN, List.of(new NumExpr.Lit(50),
                new NumExpr.Bin(new NumExpr.Var(0), NumExpr.Op.MULTIPLY, new NumExpr.Lit(10))));

        assertEquals(GateOutcome.ACTIVATED,
                pipeline.evaluate(a.build(), act().facts(facts).chanceRoll(() -> 19.0).build()));
        assertEquals(GateOutcome.CHANCE_FAILED,
                pipeline.evaluate(a.build(), act().facts(facts).chanceRoll(() -> 21.0).build()));

        facts.setNumber(0, 8.0); // 80 raw, capped by the author's own min() at 50
        assertEquals(GateOutcome.ACTIVATED,
                pipeline.evaluate(a.build(), act().facts(facts).chanceRoll(() -> 49.0).build()));
        assertEquals(GateOutcome.CHANCE_FAILED,
                pipeline.evaluate(a.build(), act().facts(facts).chanceRoll(() -> 51.0).build()));
    }

    @Test
    void expressionChanceClampsToTheLegalPercentRange() {
        FactBuffer facts = new FactBuffer(1, 0, 0);
        Ab a = new Ab();
        a.chanceExpr = new NumExpr.Var(0);

        facts.setNumber(0, 900.0); // over 100 → always fires, never a roll above the range
        assertEquals(GateOutcome.ACTIVATED,
                pipeline.evaluate(a.build(), act().facts(facts).chanceRoll(() -> 99.999).build()));

        facts.setNumber(0, -50.0); // under 0 → never fires
        assertEquals(GateOutcome.CHANCE_FAILED,
                pipeline.evaluate(a.build(), act().facts(facts).chanceRoll(() -> 0.0).build()));
    }

    @Test
    void aConditionChanceDeltaStillAppliesOnTopOfAnExpression() {
        FactBuffer facts = new FactBuffer(1, 0, 0);
        facts.setNumber(0, 10.0);
        Ab a = new Ab();
        a.chanceExpr = new NumExpr.Var(0);
        a.condition = new CompiledCondition(new Cond.BoolLit(true), FlowKind.CONTINUE, FlowKind.CONTINUE,
                25.0, Source.UNKNOWN);
        // 10 from the expression + 25 from the clause = 35
        assertEquals(GateOutcome.ACTIVATED,
                pipeline.evaluate(a.build(), act().facts(facts).chanceRoll(() -> 34.0).build()));
        assertEquals(GateOutcome.CHANCE_FAILED,
                pipeline.evaluate(a.build(), act().facts(facts).chanceRoll(() -> 36.0).build()));
    }

    @Test
    void aConstantChanceIsUnaffectedByTheExpressionPath() {
        // The fast path: no expression means the primitive double is read exactly as before.
        Ab a = new Ab();
        a.baseChance = 50.0;
        assertEquals(GateOutcome.CHANCE_FAILED, pipeline.evaluate(a.build(), act().chanceRoll(() -> 75.0).build()));
        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(a.build(), act().chanceRoll(() -> 25.0).build()));
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
    void aCarriedSoulCostAbilityFiresOutsideSoulModeAndStillPays() {
        Ab a = new Ab();
        a.soulCost = 3;
        a.soulCostCarried = true;
        spender.balance = 5;

        // The gem is a wallet, not a switch: no active gem, but the carried gems can pay, so it fires.
        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(a.build(), act().build()));
        assertTrue(spender.carriedPathUsed, "the carried path is what paid, not the soul-mode one");
        assertEquals(2, spender.balance, "the cost is really charged — it is not a free pass");
    }

    @Test
    void aCarriedSoulCostAbilityStillBlocksWhenTheCarriedGemsCannotPay() {
        Ab a = new Ab();
        a.soulCost = 3;
        a.soulCostCarried = true;
        spender.balance = 2;

        assertEquals(GateOutcome.NO_SOULS, pipeline.evaluate(a.build(), act().build()));
        assertEquals(2, spender.balance, "a refused spend leaves the balance alone");
    }

    @Test
    void aCarriedSoulCostAbilityInSoulModeTakesTheOrdinaryPath() {
        Ab a = new Ab();
        a.soulCost = 3;
        a.soulCostCarried = true;
        spender.balance = 5;

        // With a gem active the flag changes nothing: one ledger, so the two paths can never double-spend.
        assertEquals(GateOutcome.ACTIVATED,
                pipeline.evaluate(a.build(), act().soulMode(UUID.randomUUID()).build()));
        assertFalse(spender.carriedPathUsed);
        assertEquals(2, spender.balance);
    }

    @Test
    void anEscalatingSoulCostChargesTheLadderAndHoldsAtTheCap() {
        // The shipped consumer (Phoenix): 500 → 1000 → 2000 → 4000 → 8000, capped. The counter advances only
        // on a SUCCESSFUL charge, so the prices must come out of gate 10 in exactly that order.
        Ab a = new Ab();
        a.soulCost = 500;
        a.soulCostGrowth = 2.0;
        a.soulCostCap = 8000;
        a.cdEnchant = 4;
        spender.balance = 1_000_000;

        assertEquals(List.of(500, 1000, 2000, 4000, 8000, 8000), charge(a, 6));
    }

    @Test
    void aFailedSpendDoesNotAdvanceTheLadder() {
        // A gate-10 abort must leave the price where it was: an unaffordable proc that still bumped the counter
        // would price the ability further out of reach on every retry — an unpayable death spiral.
        Ab a = new Ab();
        a.soulCost = 500;
        a.soulCostGrowth = 2.0;
        a.cdEnchant = 4;

        spender.balance = 0;
        assertEquals(GateOutcome.NO_SOULS, pipeline.evaluate(a.build(), act().soulMode(ACTOR).build()));
        assertEquals(GateOutcome.NO_SOULS, pipeline.evaluate(a.build(), act().soulMode(ACTOR).build()));

        spender.balance = 500;
        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(a.build(), act().soulMode(ACTOR).build()));
        assertEquals(0, spender.balance, "the first successful charge still pays the BASE price");
    }

    @Test
    void aStaticSoulCostChargesTheBasePriceForever() {
        // Back-compat: growth defaults to 1.0, so an ability authored before the knobs existed never escalates.
        Ab a = new Ab();
        a.soulCost = 25;
        a.cdEnchant = 4;
        spender.balance = 1000;

        assertEquals(List.of(25, 25, 25, 25), charge(a, 4));
    }

    /** The souls actually debited by {@code charges} consecutive activations of {@code a}. */
    private List<Integer> charge(Ab a, int charges) {
        Ability built = a.build();
        List<Integer> paid = new java.util.ArrayList<>();
        for (int i = 0; i < charges; i++) {
            int before = spender.balance;
            assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(built, act().soulMode(ACTOR).build()));
            paid.add(before - spender.balance);
        }
        return paid;
    }

    @Test
    void cooldownIsArmedOnlyOnActivation() {
        Ab a = new Ab();
        a.cdEnchant = 1;
        a.cdGroup = 2;
        a.cooldownTicks = 40;
        pipeline.evaluate(a.build(), act().build()); // ACTIVATED → arms the ENCHANT scope only
        assertFalse(cooldowns.ready(ACTOR, CooldownStore.key(0, 1), 100L)); // enchant scope armed
        assertTrue(cooldowns.ready(ACTOR, CooldownStore.key(1, 2), 100L));  // group id is suppression-only: never armed
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
    void aScopelessAbilityNeitherBlocksOnNorArmsTheSharedBucket() {
        // The `cooldown-scope: none` opt-out reaches gate 6 as cdEnchant == -1: the authored cooldown is still
        // carried, but no bucket is checked or armed, so the ability fires every hit AND leaves the scope its
        // siblings share untouched (Rocket Escape's FALL companion, starved by its own launch arming the bucket).
        Ab optedOut = new Ab();
        optedOut.cdEnchant = -1;
        optedOut.cooldownTicks = 100;
        Ab sibling = new Ab();
        sibling.cdEnchant = 4;   // the bucket the opted-out ability would otherwise have shared
        sibling.cooldownTicks = 100;

        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(optedOut.build(), act().build()));
        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(optedOut.build(), act().build())); // same tick
        assertTrue(cooldowns.ready(ACTOR, CooldownStore.key(0, 4, 0), 100L), "the shared bucket was never armed");
        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(sibling.build(), act().build()));
    }

    @Test
    void perVictimCooldownGivesEachVictimItsOwnWindow() {
        // Thundering Blow's shape: a 50-tick cooldown that must throttle repeat strikes on ONE target without
        // the coarse mob/player bucket letting the first mob hit lock out every other mob in the pack.
        UUID victimA = UUID.randomUUID();
        UUID victimB = UUID.randomUUID();
        Ab a = new Ab();
        a.cdEnchant = 1;
        a.cooldownTicks = 40;
        a.cooldownPerVictim = true;

        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(a.build(), act().victimId(victimA).build()));
        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(a.build(), act().victimId(victimB).build()));
        assertEquals(GateOutcome.ON_COOLDOWN, pipeline.evaluate(a.build(), act().victimId(victimA).build()));
        // The {TIME_FORMATTED} read-back must land on the key the gate reserved, per victim.
        assertEquals(40L, pipeline.remainingCooldownTicks(a.build(), act().victimId(victimA).build()));
        assertEquals(0L, pipeline.remainingCooldownTicks(a.build(),
                Activation.builder(ACTOR, 3, 0, 140L).victimId(victimA).build()));
    }

    @Test
    void aNonPerVictimAbilityStillSharesOneBucketAcrossVictims() {
        // The default must stay byte-identical: without the opt-in, every mob shares the bucket-0 route.
        Ab a = new Ab();
        a.cdEnchant = 1;
        a.cooldownTicks = 40;

        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(a.build(), act().victimId(UUID.randomUUID()).build()));
        assertEquals(GateOutcome.ON_COOLDOWN, pipeline.evaluate(a.build(), act().victimId(UUID.randomUUID()).build()));
    }

    @Test
    void aPerVictimAbilityWithNoVictimFallsBackToTheCoarseBucket() {
        // A non-combat trigger carries no victim. Falling back to the coarse bucket keeps the authored cooldown
        // enforced; the alternative (no key at all) would silently disable it and let the ability fire every tick.
        Ab a = new Ab();
        a.cdEnchant = 1;
        a.cooldownTicks = 40;
        a.cooldownPerVictim = true;

        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(a.build(), act().build()));
        assertEquals(GateOutcome.ON_COOLDOWN, pipeline.evaluate(a.build(), act().build()));
        assertFalse(cooldowns.ready(ACTOR, CooldownStore.key(0, 1, 0), 100L));
    }

    @Test
    void perVictimChanceFailReleasesThatVictimsReservationNotTheCoarseOne() {
        // The rollback has to name the same key the reservation used; releasing the coarse key instead would
        // leave the victim's window armed and turn a sub-100% proc into a one-shot-per-window ability.
        UUID victim = UUID.randomUUID();
        Ab a = new Ab();
        a.cdEnchant = 3;
        a.cooldownTicks = 40;
        a.baseChance = 50.0;
        a.cooldownPerVictim = true;

        assertEquals(GateOutcome.CHANCE_FAILED,
                pipeline.evaluate(a.build(), act().victimId(victim).chanceRoll(() -> 75.0).build()));
        assertEquals(GateOutcome.ACTIVATED,
                pipeline.evaluate(a.build(), act().victimId(victim).chanceRoll(() -> 25.0).build()));
        assertTrue(cooldowns.ready(ACTOR, CooldownStore.key(0, 3, 0), 100L),
                "the coarse bucket is never touched by a per-victim ability");
    }

    @Test
    void aPerVictimCooldownDoesNotResetTheSoulCostLadderPerVictim() {
        // The escalation counter is per-actor-per-ability: folding the victim into it would let a player reset
        // an escalating price just by switching targets.
        Ab a = new Ab();
        a.cdEnchant = 4;
        a.cooldownPerVictim = true;
        a.soulCost = 500;
        a.soulCostGrowth = 2.0;
        spender.balance = 1_000_000;

        int before = spender.balance;
        assertEquals(GateOutcome.ACTIVATED,
                pipeline.evaluate(a.build(), act().victimId(UUID.randomUUID()).soulMode(ACTOR).build()));
        assertEquals(500, before - spender.balance);
        before = spender.balance;
        assertEquals(GateOutcome.ACTIVATED,
                pipeline.evaluate(a.build(), act().victimId(UUID.randomUUID()).soulMode(ACTOR).build()));
        assertEquals(1000, before - spender.balance, "a fresh victim must not rewind the ladder");
    }

    @Test
    void groupIdNeverParticipatesInCooldownsSoASiblingCannotLockRageOut() {
        // ADR-0050 R4 field regression: any cooldown-carrying enchant used to arm its whole GROUP scope, and a
        // check-only cooldown-0 same-group sibling (rage, armored) was then refused fight-long. Group/type ids
        // belong to gate-5 suppression matching only — they must neither arm nor block at gate 6.
        Ab lifesteal = new Ab();
        lifesteal.cdEnchant = 1;
        lifesteal.cdGroup = 2;      // "legendary"
        lifesteal.cooldownTicks = 100;
        Ab rage = new Ab();
        rage.cdEnchant = 3;
        rage.cdGroup = 2;           // same group, cooldown 0

        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(lifesteal.build(), act().build()));
        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(rage.build(), act().build())); // same tick, same walk

        // Even a directly armed GROUP key cannot block: gate 6 no longer consults the group scope at all.
        cooldowns.arm(ACTOR, CooldownStore.key(1, 2, 0), 100L, 40);
        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(rage.build(), act().build()));
    }
}
