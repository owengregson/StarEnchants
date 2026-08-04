package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Per-victim frozen windows (FREEZE, ADR-0065) — the registry the sink's chain and the modern guard consult.
 * Lattice ticks advance explicitly through {@link FrozenTargets#chainTick} and {@link FrozenTargets#isFrozen}
 * reads that same budget, so the timing is exact and server-free; the attacker handle is never dereferenced,
 * so {@code null} stands in for a live entity.
 * (Style mirrors {@link LockedPotionsTest}: one plain test per contract, hand-computed expectations.)
 */
class FrozenTargetsTest {

    private final UUID victim = UUID.randomUUID();

    @AfterEach
    void clean() {
        FrozenTargets.teardownAll();
    }

    @Test
    void livenessIsTheTickBudgetNotTheWallDeadline() {
        long gen = FrozenTargets.arm(victim, 60, System.currentTimeMillis() + 10_000, UUID.randomUUID(), null);
        assertTrue(FrozenTargets.isFrozen(victim), "live from arm");
        FrozenTargets.chainTick(victim, gen, 20);
        FrozenTargets.chainTick(victim, gen, 20);
        assertTrue(FrozenTargets.isFrozen(victim), "still live with budget left, wall deadline far away");
        FrozenTargets.chainTick(victim, gen, 20); // the boundary slot — the chain tears down in this same run
        assertFalse(FrozenTargets.isFrozen(victim), "the spent budget ends the window");
        assertNotNull(FrozenTargets.get(victim), "a liveness read never evicts: the owning chain disarms itself");
    }

    @Test
    void aWallLapsedWindowStaysFrozenWhileItsTickBudgetRuns() {
        // The laggy tail: below 20 TPS a 60-tick window outlasts its 3000 ms wall deadline while the victim is
        // still pinned by Paper's freeze-tick lock. A wall-clock read went blind here, so vanilla's fully-frozen
        // self-hurt landed and its i-frames partialled the next DoT slot on <=1.20.6 (the folia:1.20.6 flake).
        long gen = FrozenTargets.arm(victim, 60, System.currentTimeMillis() - 1, UUID.randomUUID(), null);
        FrozenTargets.chainTick(victim, gen, 20); // mid-window: 20 of 60 lattice ticks claimed
        assertTrue(FrozenTargets.isFrozen(victim), "the tick budget outlives the wall deadline under lag");
    }

    @Test
    void chainClaimsExactlyTheBudgetBoundaryInclusive() {
        long now = System.currentTimeMillis();
        long gen = FrozenTargets.arm(victim, 60, now + 3_000, null, null);
        assertNotNull(FrozenTargets.chainTick(victim, gen, 20)); // t+20
        assertNotNull(FrozenTargets.chainTick(victim, gen, 20)); // t+40
        FrozenTargets.Window boundary = FrozenTargets.chainTick(victim, gen, 20); // t+60 == duration
        assertNotNull(boundary, "the t+duration boundary slot is inclusive");
        assertFalse(boundary.hasNextSlot(20), "the boundary slot is the final one — the chain tears down this run");
        assertNull(FrozenTargets.chainTick(victim, gen, 20), "no slot past the budget");
    }

    @Test
    void chainTickRejectsAStaleGenerationOrAnAbsentWindow() {
        long now = System.currentTimeMillis();
        assertNull(FrozenTargets.chainTick(victim, 1L, 20), "no window, no slot");
        long stale = FrozenTargets.arm(victim, 60, now + 3_000, null, null);
        long current = FrozenTargets.arm(victim, 60, now + 3_000, null, null);
        assertNull(FrozenTargets.chainTick(victim, stale, 20), "a superseded chain claims nothing");
        assertNotNull(FrozenTargets.chainTick(victim, current, 20));
    }

    @Test
    void refreshExtendsTheBudgetFromTheLastCompletedSlotAndSwapsTheAttacker() {
        long now = System.currentTimeMillis();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        long gen = FrozenTargets.arm(victim, 40, now + 2_000, first, null);
        assertNotNull(FrozenTargets.chainTick(victim, gen, 20)); // t+20 consumed before the re-proc

        assertTrue(FrozenTargets.refresh(victim, 60, now + 4_000, second, null), "a live window refreshes");
        FrozenTargets.Window w = FrozenTargets.get(victim);
        assertEquals(gen, w.generation, "the generation is kept (no second window)");
        assertEquals(now + 4_000, w.deadlineMs, "a later deadline extends the window");
        assertEquals(second, w.attackerId, "the attacker is retargeted");
        // The budget anchors at the last completed slot: 20 + 60 = 80 → exactly three more 20-tick slots.
        assertNotNull(FrozenTargets.chainTick(victim, gen, 20)); // t+40
        assertNotNull(FrozenTargets.chainTick(victim, gen, 20)); // t+60
        assertNotNull(FrozenTargets.chainTick(victim, gen, 20), "the refreshed boundary t+80 lands");
        assertNull(FrozenTargets.chainTick(victim, gen, 20), "nothing past the refreshed budget");
    }

    @Test
    void refreshNeverShortensTheBudgetOrTheDeadline() {
        long now = System.currentTimeMillis();
        long gen = FrozenTargets.arm(victim, 100, now + 5_000, null, null);
        assertTrue(FrozenTargets.refresh(victim, 20, now + 1_000, null, null));
        assertEquals(now + 5_000, FrozenTargets.get(victim).deadlineMs,
                "an EARLIER deadline never shortens the window");
        // The 100-tick budget outlives the 0+20 refresh: all five 20-tick slots still land.
        for (int slot = 1; slot <= 5; slot++) {
            assertNotNull(FrozenTargets.chainTick(victim, gen, 20), "slot " + slot);
        }
        assertNull(FrozenTargets.chainTick(victim, gen, 20));
    }

    @Test
    void refreshOnAnAbsentOrWallLapsedWindowReturnsFalse() {
        long now = System.currentTimeMillis();
        assertFalse(FrozenTargets.refresh(victim, 60, now + 1_000, UUID.randomUUID(), null), "no window to refresh");
        FrozenTargets.arm(victim, 60, now - 1, UUID.randomUUID(), null); // wall lapsed: a presumed-dead chain
        assertFalse(FrozenTargets.refresh(victim, 60, now + 1_000, UUID.randomUUID(), null),
                "a lapsed window is not refreshed (arm supersedes it instead)");
    }

    @Test
    void armSupersedesRunningTheSurvivorsTeardownFirst() {
        long now = System.currentTimeMillis();
        long stale = FrozenTargets.arm(victim, 60, now + 5_000, null, null);
        AtomicInteger ran = new AtomicInteger();
        FrozenTargets.onTeardown(victim, stale, ran::incrementAndGet);

        long current = FrozenTargets.arm(victim, 60, now + 5_000, null, null);
        assertEquals(1, ran.get(), "the superseded window's teardown ran at arm (no stale clobber later)");
        assertEquals(current, FrozenTargets.get(victim).generation, "the new window is live");
        assertTrue(FrozenTargets.isFrozen(victim));
    }

    @Test
    void disarmOnlyRemovesItsOwnGeneration() {
        long now = System.currentTimeMillis();
        long stale = FrozenTargets.arm(victim, 60, now + 5_000, UUID.randomUUID(), null);
        long current = FrozenTargets.arm(victim, 60, now + 5_000, UUID.randomUUID(), null); // re-arm supersedes

        FrozenTargets.disarm(victim, stale);
        assertTrue(FrozenTargets.isFrozen(victim), "a stale-generation disarm leaves the newer window intact");
        assertEquals(current, FrozenTargets.get(victim).generation);

        FrozenTargets.disarm(victim, current);
        assertFalse(FrozenTargets.isFrozen(victim), "the current-generation disarm removes the window");
    }

    @Test
    void teardownAllRunsEachTeardownOnceAndClears() {
        long now = System.currentTimeMillis();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        long genA = FrozenTargets.arm(a, 60, now + 5_000, null, null);
        long genB = FrozenTargets.arm(b, 60, now + 5_000, null, null);
        AtomicInteger ranA = new AtomicInteger();
        AtomicInteger ranB = new AtomicInteger();
        FrozenTargets.onTeardown(a, genA, ranA::incrementAndGet);
        FrozenTargets.onTeardown(b, genB, ranB::incrementAndGet);

        FrozenTargets.teardownAll();
        assertEquals(1, ranA.get());
        assertEquals(1, ranB.get());
        assertFalse(FrozenTargets.isFrozen(a), "cleared");
        assertFalse(FrozenTargets.isFrozen(b), "cleared");

        FrozenTargets.teardownAll();
        assertEquals(1, ranA.get(), "a second sweep is a no-op (no windows)");
        assertEquals(1, ranB.get());
    }

    @Test
    void onTeardownWithAStaleGenerationDoesNotAttach() {
        long now = System.currentTimeMillis();
        long gen = FrozenTargets.arm(victim, 60, now + 5_000, null, null);
        AtomicInteger ran = new AtomicInteger();
        FrozenTargets.onTeardown(victim, gen + 999, ran::incrementAndGet); // stale — must not attach

        FrozenTargets.teardownAll();
        assertEquals(0, ran.get(), "a stale-generation teardown was never attached, so it never ran");
    }
}
