package item.codec;

import java.util.UUID;

/**
 * A soul-gem's on-item state (§4.2, §6.3): a stable {@code gemId} (the gem's per-item identity, so two
 * stacks can never share a balance) and the current {@code souls} count. Stored under
 * {@link ItemKeys#soul()}, separate from the {@link CombatState} blob so per-kill/spend changes don't
 * invalidate the content-hash {@code ItemView} cache (or re-render lore) per hit.
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

    public static SoulData fresh(UUID gemId) {
        return new SoulData(gemId, 0);
    }

    public SoulData withSouls(int next) {
        return new SoulData(gemId, next);
    }
}
