package engine.sink;

import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * The soul collaborator {@link DispatchSink} calls for {@code REMOVE_SOULS} — the seam to the feature soul
 * system, mirroring {@code EconomyService} for money (§D, §6.3). A debit is charged against the in-memory
 * soul pool and drained least-first from the gems' PDC <em>on the holder's own thread</em>: the gems live
 * in the holder's inventory, so an off-region PDC write would be a Folia cross-region bug.
 */
@FunctionalInterface
public interface SoulDebit {

    /**
     * Debit {@code amount} souls from {@code holder}'s gem {@code gemId}, persisting the new count to the
     * gem wherever it sits in the holder's inventory. MUST run on the holder's own thread (the
     * {@link DispatchSink} guarantees this). No-op for a non-positive amount or a non-active gem.
     */
    void debit(Player holder, UUID gemId, int amount);

    /**
     * Debit {@code amount} souls from {@code target}'s OWN active gem, resolving which from the soul-mode
     * store ({@code REMOVE_SOULS:…:@Victim} — drain the enemy's souls). No-op if the target is not in soul
     * mode. Like {@link #debit}, MUST run on {@code target}'s own thread. Defaults to a no-op (overridden
     * by the feature soul service) so the functional-interface lambdas and {@link #NONE} keep compiling.
     */
    default void debitTarget(Player target, int amount) {
    }

    /** No soul system wired — every debit is a no-op. */
    SoulDebit NONE = (holder, gemId, amount) -> { };
}
