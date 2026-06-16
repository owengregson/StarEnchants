package item.codec;

import java.util.UUID;

/**
 * A soul-gem's on-item state (docs/architecture.md §4.2, §6.3): a stable {@code gemId} (the key the
 * {@code SoulLedger} tracks its in-memory authority under, so two stacks can never share a balance)
 * and the current {@code souls} count. Stored under {@link ItemKeys#soul()}, SEPARATE from the
 * {@link CombatState} blob — souls change on every kill/spend, so keeping them out of the combat
 * blob avoids invalidating the content-hash {@code ItemView} cache (and re-rendering lore) per hit.
 *
 * @param gemId the stable per-item gem identity (the ledger key); never {@code null}
 * @param souls the current soul balance ({@code >= 0})
 */
public record SoulData(UUID gemId, int souls) {

    public SoulData {
        if (gemId == null) {
            throw new IllegalArgumentException("gemId");
        }
        souls = Math.max(0, souls);
    }

    /** A fresh, empty gem with a new identity. */
    public static SoulData fresh(UUID gemId) {
        return new SoulData(gemId, 0);
    }

    /** This gem with a new soul count. */
    public SoulData withSouls(int next) {
        return new SoulData(gemId, next);
    }
}
