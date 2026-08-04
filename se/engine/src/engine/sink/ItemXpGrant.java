package engine.sink;

import org.bukkit.entity.Player;

/**
 * The collaborator {@code ITEM_XP_TRACK} grants through — the seam to the feature layer's item progression,
 * mirroring {@link SoulDebit} for souls. Item-attached levelling is the pets family's business (which item
 * carries progression, what its curve is, how its lore re-renders), so the engine only holds the call.
 */
@FunctionalInterface
public interface ItemXpGrant {

    /**
     * Credit {@code amount} progression XP to the item {@code holder} is HOLDING — the item that fired this
     * activation, which is the only one an effect can name. {@code windowMinutes > 0} gates the grant to once
     * per window on a stamp carried BY the item, so the gate survives a trade. {@code gainMessage} /
     * {@code levelUpMessage} are the authored lines (empty = silent). MUST run on {@code holder}'s own thread
     * (the {@link Sink} guarantees this): it reads and writes their inventory.
     */
    void grant(Player holder, int amount, int windowMinutes, String gainMessage, String levelUpMessage);

    /** No progression system wired — every grant is a no-op. */
    ItemXpGrant NONE = (holder, amount, windowMinutes, gainMessage, levelUpMessage) -> { };
}
