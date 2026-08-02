package feature.bless;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import platform.economy.EconomyProvider;
import platform.economy.EconomyService;

/**
 * The {@code /bless} cost + cooldown policy (ADR-0072). Time is an explicit caller-supplied millisecond stamp,
 * so these are deterministic and need no server. The invariant worth guarding hardest: nothing is charged and
 * no window is armed unless the whole gate passes — a refused bless must never cost a player anything.
 */
class BlessGateTest {

    private static final UUID PLAYER = UUID.nameUUIDFromBytes("player".getBytes());

    /** An economy that records every withdrawal and can be told to decline. */
    private static final class Wallet implements EconomyProvider {
        double balance;
        double withdrawn;

        Wallet(double balance) {
            this.balance = balance;
        }

        @Override public String name() { return "test"; }

        @Override public double balance(UUID player) { return balance; }

        @Override public boolean withdraw(UUID player, double amount) {
            if (amount > balance) {
                return false;
            }
            balance -= amount;
            withdrawn += amount;
            return true;
        }

        @Override public void deposit(UUID player, double amount) { balance += amount; }
    }

    private static BlessGate gate(BlessGate.Settings settings, EconomyService economy) {
        return new BlessGate(() -> settings, economy);
    }

    @Test
    void theFirstBlessPassesAndArmsTheCooldown() {
        BlessGate gate = gate(new BlessGate.Settings(60, 0), EconomyService.NONE);

        assertTrue(gate.claim(PLAYER, 0L).allowed());

        BlessGate.Decision second = gate.claim(PLAYER, 1_000L);
        assertEquals(BlessGate.Verdict.COOLING_DOWN, second.verdict());
        assertEquals(59L, second.remainingSeconds());
    }

    @Test
    void theCooldownReleasesExactlyWhenItElapses() {
        BlessGate gate = gate(new BlessGate.Settings(60, 0), EconomyService.NONE);
        gate.claim(PLAYER, 0L);

        assertFalse(gate.claim(PLAYER, 59_999L).allowed(), "one millisecond short is still cooling down");
        assertTrue(gate.claim(PLAYER, 60_000L).allowed(), "the window opens on the stroke");
    }

    @Test
    void remainingSecondsRoundsUpSoItNeverReadsZeroWhileCoolingDown() {
        BlessGate gate = gate(new BlessGate.Settings(60, 0), EconomyService.NONE);
        gate.claim(PLAYER, 0L);
        assertEquals(1L, gate.claim(PLAYER, 59_500L).remainingSeconds(), "half a second left reads as 1s, not 0s");
    }

    @Test
    void aZeroCooldownNeverGates() {
        BlessGate gate = gate(new BlessGate.Settings(0, 0), EconomyService.NONE);
        assertTrue(gate.claim(PLAYER, 0L).allowed());
        assertTrue(gate.claim(PLAYER, 0L).allowed(), "same millisecond, still allowed");
    }

    @Test
    void anAffordableCostIsChargedExactlyOnce() {
        Wallet wallet = new Wallet(100);
        BlessGate gate = gate(new BlessGate.Settings(0, 25), new EconomyService(wallet));

        assertTrue(gate.claim(PLAYER, 0L).allowed());
        assertEquals(25.0, wallet.withdrawn);
        assertEquals(75.0, wallet.balance);
    }

    @Test
    void anUnaffordableBlessIsRefusedAndArmsNoCooldown() {
        Wallet wallet = new Wallet(10);
        BlessGate gate = gate(new BlessGate.Settings(60, 25), new EconomyService(wallet));

        BlessGate.Decision refused = gate.claim(PLAYER, 0L);
        assertEquals(BlessGate.Verdict.CANNOT_AFFORD, refused.verdict());
        assertEquals(25.0, refused.cost());
        assertEquals(0.0, wallet.withdrawn, "a refusal never takes money");

        // The cooldown must NOT have been armed — a player who could not pay has not used their bless.
        wallet.balance = 100;
        assertTrue(gate.claim(PLAYER, 1L).allowed(), "still able to bless the moment they can afford it");
    }

    @Test
    void aCostWithNoEconomyRefusesRatherThanRunningFree() {
        BlessGate gate = gate(new BlessGate.Settings(60, 25), EconomyService.NONE);

        assertEquals(BlessGate.Verdict.NO_ECONOMY, gate.claim(PLAYER, 0L).verdict());
        // Nothing armed either: the misconfiguration must not also burn the player's cooldown.
        assertEquals(BlessGate.Verdict.NO_ECONOMY, gate.claim(PLAYER, 1L).verdict());
    }

    @Test
    void aCooldownRefusalIsCheckedBeforeAnyCharge() {
        Wallet wallet = new Wallet(100);
        BlessGate gate = gate(new BlessGate.Settings(60, 25), new EconomyService(wallet));

        assertTrue(gate.claim(PLAYER, 0L).allowed());
        assertEquals(25.0, wallet.withdrawn);

        assertEquals(BlessGate.Verdict.COOLING_DOWN, gate.claim(PLAYER, 1_000L).verdict());
        assertEquals(25.0, wallet.withdrawn, "a cooling-down bless is not charged");
    }

    @Test
    void settingsAreReReadEveryAttemptSoAReloadTakesEffect() {
        AtomicReference<BlessGate.Settings> live = new AtomicReference<>(new BlessGate.Settings(60, 0));
        BlessGate gate = new BlessGate(live::get, EconomyService.NONE);

        assertTrue(gate.claim(PLAYER, 0L).allowed());
        assertFalse(gate.claim(PLAYER, 1_000L).allowed());

        live.set(new BlessGate.Settings(0, 0)); // operator turns the cooldown off mid-run
        assertTrue(gate.claim(PLAYER, 1_000L).allowed(),
                "cooldown 0 means no cooldown — including for a window armed before the knob was turned down");
    }

    @Test
    void forgetElapsedDropsOnlyWindowsThatHavePassed() {
        BlessGate gate = gate(new BlessGate.Settings(60, 0), EconomyService.NONE);
        gate.claim(PLAYER, 0L);

        gate.forgetElapsed(1_000L);
        assertFalse(gate.claim(PLAYER, 1_000L).allowed(), "a live window survives the sweep");

        gate.forgetElapsed(60_000L);
        assertTrue(gate.claim(PLAYER, 60_000L).allowed());
    }
}
