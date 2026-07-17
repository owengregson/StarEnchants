package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Per-victim frozen windows (FREEZE, ADR-0065) — the registry the sink's tasks and the modern guard consult.
 * Wall-clock deadlines are supplied explicitly to {@link FrozenTargets#isFrozen} so the timing is exact and
 * server-free; the attacker handle is never dereferenced, so {@code null} stands in for a live entity.
 * (Style mirrors {@link LockedPotionsTest}: one plain test per contract, hand-computed expectations.)
 */
class FrozenTargetsTest {

    private final UUID victim = UUID.randomUUID();

    @AfterEach
    void clean() {
        FrozenTargets.teardownAll();
    }

    @Test
    void armMakesFrozenUntilTheDeadlineThenSelfEvicts() {
        long now = System.currentTimeMillis();
        FrozenTargets.arm(victim, now + 10_000, UUID.randomUUID(), null);
        assertTrue(FrozenTargets.isFrozen(victim, now), "live before the deadline");
        assertFalse(FrozenTargets.isFrozen(victim, now + 10_000), "not live at/after the deadline");
        assertFalse(FrozenTargets.isFrozen(victim, now), "the lapsed entry self-evicted on the previous read");
    }

    @Test
    void refreshKeepsTheGenerationExtendsTheDeadlineAndSwapsTheAttacker() {
        long now = System.currentTimeMillis();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        long gen = FrozenTargets.arm(victim, now + 5_000, first, null);

        assertTrue(FrozenTargets.refresh(victim, now + 2_000, second, null), "a live window refreshes");
        FrozenTargets.Window w = FrozenTargets.get(victim);
        assertEquals(gen, w.generation, "the generation is kept (no second window)");
        assertEquals(now + 5_000, w.deadlineMs, "an EARLIER deadline never shortens the window");
        assertEquals(second, w.attackerId, "the attacker is retargeted");

        FrozenTargets.refresh(victim, now + 9_000, first, null);
        assertEquals(now + 9_000, FrozenTargets.get(victim).deadlineMs, "a later deadline extends the window");
    }

    @Test
    void refreshOnAnAbsentOrLapsedWindowReturnsFalse() {
        long now = System.currentTimeMillis();
        assertFalse(FrozenTargets.refresh(victim, now + 1_000, UUID.randomUUID(), null), "no window to refresh");
        FrozenTargets.arm(victim, now - 1, UUID.randomUUID(), null); // already past
        assertFalse(FrozenTargets.refresh(victim, now + 1_000, UUID.randomUUID(), null), "a lapsed window is not refreshed");
    }

    @Test
    void disarmOnlyRemovesItsOwnGeneration() {
        long now = System.currentTimeMillis();
        long stale = FrozenTargets.arm(victim, now + 5_000, UUID.randomUUID(), null);
        long current = FrozenTargets.arm(victim, now + 5_000, UUID.randomUUID(), null); // re-arm supersedes

        FrozenTargets.disarm(victim, stale);
        assertTrue(FrozenTargets.isFrozen(victim, now), "a stale-generation disarm leaves the newer window intact");
        assertEquals(current, FrozenTargets.get(victim).generation);

        FrozenTargets.disarm(victim, current);
        assertFalse(FrozenTargets.isFrozen(victim, now), "the current-generation disarm removes the window");
    }

    @Test
    void teardownAllRunsEachTeardownOnceAndClears() {
        long now = System.currentTimeMillis();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        long genA = FrozenTargets.arm(a, now + 5_000, null, null);
        long genB = FrozenTargets.arm(b, now + 5_000, null, null);
        AtomicInteger ranA = new AtomicInteger();
        AtomicInteger ranB = new AtomicInteger();
        FrozenTargets.onTeardown(a, genA, ranA::incrementAndGet);
        FrozenTargets.onTeardown(b, genB, ranB::incrementAndGet);

        FrozenTargets.teardownAll();
        assertEquals(1, ranA.get());
        assertEquals(1, ranB.get());
        assertFalse(FrozenTargets.isFrozen(a, now), "cleared");
        assertFalse(FrozenTargets.isFrozen(b, now), "cleared");

        FrozenTargets.teardownAll();
        assertEquals(1, ranA.get(), "a second sweep is a no-op (no windows)");
        assertEquals(1, ranB.get());
    }

    @Test
    void onTeardownWithAStaleGenerationDoesNotAttach() {
        long now = System.currentTimeMillis();
        long gen = FrozenTargets.arm(victim, now + 5_000, null, null);
        AtomicInteger ran = new AtomicInteger();
        FrozenTargets.onTeardown(victim, gen + 999, ran::incrementAndGet); // stale — must not attach

        FrozenTargets.teardownAll();
        assertEquals(0, ran.get(), "a stale-generation teardown was never attached, so it never ran");
    }
}
