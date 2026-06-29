package feature.soul;

import java.util.UUID;

/**
 * Soul context for one activation (§6.3, §D): a non-null marker present iff the activator is in soul mode. The
 * spend authority is the player's cross-gem {@code SoulPool} (gate 10 spends it via {@code SoulSpender}); this
 * marker just flags soul mode on the {@code Activation}/{@code EffectCtx} (and rides through to
 * {@code REMOVE_SOULS}'s sink call, where the holder's gems are drained least-first).
 */
public record SoulBinding(UUID marker) {
}
