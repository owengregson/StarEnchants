package engine.interact;

import java.util.UUID;

/**
 * Gate-10's collaborator for spending a soul cost (§D / §3.3). The pipeline calls {@link #trySpend} when an
 * ability has a {@code soul-cost}; the implementation ({@code feature.soul.SoulService}, backed by {@link
 * SoulPool}) charges the player's cross-gem pool atomically — so the check is correct even when gate 10 runs on
 * a foreign region thread (combat fires on the victim's region while the gem-holder is the attacker). The
 * physical gems are drained on the holder's own thread afterwards.
 */
@FunctionalInterface
public interface SoulSpender {

    /**
     * Atomically spend {@code cost} souls from {@code player}'s pool. {@code true} iff the player is in soul mode
     * AND can afford the full cost (all-or-nothing). A non-positive cost is always affordable.
     */
    boolean trySpend(UUID player, int cost);

    /** A spender that never affords anything (tests / not wired). */
    SoulSpender NONE = (player, cost) -> false;
}
