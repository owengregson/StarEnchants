package engine.sink;

import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * The soul collaborator the {@link DispatchSink} calls for {@code REMOVE_SOULS} — the engine-side seam
 * to the feature soul system, mirroring {@code EconomyService}'s role for money (docs/v3-directives.md
 * §D, docs/architecture.md §6.3). A debit is addressed by gem IDENTITY: charged against the in-memory
 * {@code SoulLedger} authority and written through to the gem's PDC <em>on the holder's own thread</em>
 * (the gem lives in the holder's inventory; writing PDC off-region would be a Folia cross-region bug, so
 * the sink routes the debit through {@code entityOp(holder, …)} before calling this).
 *
 * <p>{@link #NONE} debits nothing — the default for tests and soul-free paths, exactly as
 * {@code EconomyService.NONE} is the default economy.
 */
@FunctionalInterface
public interface SoulDebit {

    /**
     * Debit {@code amount} souls from {@code holder}'s gem {@code gemId}. The implementation persists the
     * new count to the gem wherever it sits in the holder's inventory; it MUST run on the holder's own
     * thread (the {@link DispatchSink} guarantees this). A non-positive amount, or a gem that is not the
     * seeded active one, is a no-op.
     */
    void debit(Player holder, UUID gemId, int amount);

    /**
     * Debit {@code amount} souls from {@code target}'s OWN active gem, resolving which gem that is from the
     * soul-mode store (the {@code REMOVE_SOULS:…:@Victim} case — drain the enemy's souls). A no-op if the
     * target is not in soul mode. Like {@link #debit}, MUST run on {@code target}'s own thread (the
     * {@link DispatchSink} routes it there). The default is a no-op so the functional-interface lambda
     * usages (and {@link #NONE}) keep compiling; the feature soul service overrides it.
     */
    default void debitTarget(Player target, int amount) {
        // No soul-mode lookup at this seam by default — overridden by the feature soul service.
    }

    /** No soul system wired — every debit is a no-op. */
    SoulDebit NONE = (holder, gemId, amount) -> { };
}
