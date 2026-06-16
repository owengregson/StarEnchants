package api.event;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when one of a player's abilities ACTIVATES — after every gate passed (chance, cooldown,
 * souls, …) and immediately before its effects run (docs/architecture.md §13). The public hook a
 * third-party plugin uses to react to a proc: award stats, log, play extra feedback. A notification,
 * not a veto — the ability has already activated; suppress an ability up front via your own listener
 * on the triggering Bukkit event instead.
 *
 * <p>On Folia this fires on the activating player's region thread (there is no single main thread);
 * a listener must therefore be Folia-aware and route any cross-region work through a scheduler.
 *
 * @see #getEnchantKey() the stable content key (e.g. {@code enchants/venom}), version-stable
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

    /** The player whose ability activated. Never {@code null}. */
    public Player getPlayer() {
        return player;
    }

    /**
     * The activated ability's stable content key (e.g. {@code enchants/venom}, {@code crystals/jolt}).
     * Never {@code null} — the firer skips dispatch when a key cannot be resolved.
     */
    public String getEnchantKey() {
        return enchantKey;
    }

    /** The enchant level that activated ({@code 0} for levelless sources like crystals/sets). */
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
