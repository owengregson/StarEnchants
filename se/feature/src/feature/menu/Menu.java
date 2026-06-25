package feature.menu;

import org.bukkit.entity.Player;
import platform.sched.Scheduling;

/**
 * One StarEnchants GUI on the shared menu framework (docs/v3-directives.md §K). A menu is stateless and
 * shareable — one instance serves every viewer; all per-open state lives on a per-open {@link MenuHolder}.
 * The framework wires open-hop, click routing, and re-render; a menu implements only {@link #render}.
 */
public interface Menu {

    /** The registry name this menu is opened by (e.g. {@code /se menu <name>}); lower-cased on registration. */
    String name();

    /** An extra permission node to open this menu, or {@code null} to inherit the {@code /se} admin gate. */
    default String permission() {
        return null;
    }

    /**
     * Render the menu for the holder's current navigation state. Pure aside from allocating the inventory —
     * safe to call off the player's thread ({@link #open}/{@link #reopen} schedule the actual openInventory).
     */
    void render(MenuHolder holder);

    /** Open this menu fresh for {@code player}; the openInventory hops to the player's region thread (Folia). */
    default void open(Player player) {
        MenuHolder holder = new MenuHolder(this);
        render(holder);
        Scheduling.onEntity(player, () -> player.openInventory(holder.getInventory()));
    }

    /** Re-render the holder's mutated state and re-open on the player's thread — page turns, drill-down, back. */
    default void reopen(MenuClick click) {
        render(click.holder());
        Scheduling.onEntity(click.player(), () -> click.player().openInventory(click.holder().getInventory()));
    }
}
