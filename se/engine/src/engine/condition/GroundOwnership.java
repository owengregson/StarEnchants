package engine.condition;

import java.util.UUID;

/**
 * Who placed the temporary block a body is standing on — the source behind {@code %actor.ownedground%}.
 * Bukkit-free and coordinate-keyed for the same reason {@link WornFactSource} is UUID-keyed: the fact layer
 * must not reach into the sink's world types, and a plain {@code (world, x, y, z)} tuple is answerable from
 * either era's ledger.
 *
 * <p>The composition root installs the implementation over the per-boot {@code TempBlockLedger}. Called on the
 * thread that owns the queried block, which for an actor's own feet is the thread the activation already runs
 * on.
 */
@FunctionalInterface
public interface GroundOwnership {

    /** Nothing installed: nobody owns any ground. */
    GroundOwnership NONE = (owner, world, x, y, z) -> false;

    /** Whether {@code owner} placed the temporary block at these coordinates. */
    boolean ownedBy(UUID owner, UUID world, int x, int y, int z);
}
