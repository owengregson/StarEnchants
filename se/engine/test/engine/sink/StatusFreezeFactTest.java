package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import compile.cond.VarBinding;
import compile.cond.VarKind;
import compile.model.FactMask;
import engine.condition.BuiltinVars;
import engine.condition.VarVocabulary;
import engine.run.ActivationContext;
import engine.run.FactPopulator;
import engine.run.ModernActorProbe;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@code %status.freeze%} reports {@link FrozenTargets}, the registry the FREEZE rung tears down — the guard
 * that lets a window-lifting item refuse when there is no window (wave 2e.2).
 *
 * <p>It lives beside {@code FrozenTargets} rather than in {@code FactPopulatorTest} because arming a window is
 * package-private to {@code engine.sink}: only from here can the fact be read against a REAL live window
 * instead of a stub that would agree with a mis-wired slot.
 */
class StatusFreezeFactTest {

    private static final VarVocabulary VOCAB = BuiltinVars.vocabulary();

    private final UUID actorId = UUID.randomUUID();

    @AfterEach
    void clean() {
        FrozenTargets.teardownAll();
    }

    /** The declared slot, asserted to be the kind the populator writes — a name/kind drift reads 0 in silence. */
    private static int freezeSlot() {
        VarBinding b = VOCAB.lookup("status", "freeze").orElseThrow(() -> new AssertionError("no %status.freeze%"));
        assertEquals(VarKind.BOOL, b.kind());
        return b.slot();
    }

    private Player actor() {
        Player p = mock(Player.class);
        lenient().when(p.getUniqueId()).thenReturn(actorId);
        return p;
    }

    @Test
    void theFlagFollowsTheActorsOwnLiveFreezeWindow() {
        int slot = freezeSlot();
        FactMask mask = new FactMask(0L, 1L << slot, 0L); // flags are the SECOND long
        FactPopulator pop = FactPopulator.builtin(new ModernActorProbe());
        Player a = actor();

        assertFalse(pop.populate(new ActivationContext(a, null, null, null), 0L, mask).flag(slot),
                "an actor under no freeze reads false");

        long gen = FrozenTargets.arm(actorId, 60, System.currentTimeMillis() + 3_000, UUID.randomUUID(), null);
        assertTrue(pop.populate(new ActivationContext(a, null, null, null), 0L, mask).flag(slot),
                "a live window reads true");

        // Liveness is the TICK budget, not the wall deadline (ADR-0065): the fact must spend with the chain, or
        // an item would keep offering a break-out for a window the guard has already stopped enforcing.
        FrozenTargets.chainTick(actorId, gen, 60);
        assertFalse(pop.populate(new ActivationContext(a, null, null, null), 0L, mask).flag(slot),
                "the spent budget ends the fact with the window");
    }

    @Test
    void theFlagIsPerActorAndMaskGated() {
        int slot = freezeSlot();
        FactPopulator pop = FactPopulator.builtin(new ModernActorProbe());
        FrozenTargets.arm(actorId, 60, System.currentTimeMillis() + 3_000, UUID.randomUUID(), null);

        Player frozen = actor();
        Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());

        FactMask mask = new FactMask(0L, 1L << slot, 0L);
        assertTrue(pop.populate(new ActivationContext(frozen, null, null, null), 0L, mask).flag(slot));
        assertFalse(pop.populate(new ActivationContext(other, null, null, null), 0L, mask).flag(slot),
                "a bystander must not read the frozen player's window — a crossed slot would say otherwise");
        assertFalse(pop.populate(new ActivationContext(frozen, null, null, null), 0L, FactMask.NONE).flag(slot),
                "an unreferenced fact is never populated (ADR-0039)");
    }
}
