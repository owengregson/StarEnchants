package api.event;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when an ability activates — after every gate passed, before its effects run (docs/architecture.md §13).
 * A notification, not a veto: the ability has already activated, so suppress up front via your own listener on
 * the triggering Bukkit event. On Folia this fires on the activating player's region thread — be Folia-aware.
 */
public final class EnchantActivateEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String enchantKey;
    private final int level;

    public EnchantActivateEvent(Player player, String enchantKey, int level) {
        this.player = Objects.requireNonNull(player, "player");
        this.enchantKey = Objects.requireNonNull(enchantKey, "enchantKey");
        this.level = level;
    }

    public Player getPlayer() {
        return player;
    }

    /** Stable content key (e.g. {@code enchants/venom}); version-stable and never null. */
    public String getEnchantKey() {
        return enchantKey;
    }

    /** {@code 0} for levelless sources like crystals/sets. */
    public int getLevel() {
        return level;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
