package feature.menu;

import org.bukkit.entity.Player;
import platform.sched.Scheduling;

/**
 * One StarEnchants GUI on the shared menu framework (docs/v3-directives.md §K). A menu is
 * <strong>stateless and shareable</strong>: a single instance serves every viewer, and all per-open
 * state (current page, sub-view, selection, the rendered inventory + its click actions) lives on a
 * per-open {@link MenuHolder}. The framework wires three things for free:
 *
 * <ul>
 *   <li><strong>open-hop</strong> — {@link #open} schedules the {@code openInventory} on the viewer's own
 *       region thread via {@link Scheduling#onEntity}, so a menu may be opened from any thread (a command
 *       handler runs on the global/command thread, not the player's region thread on Folia);</li>
 *   <li><strong>click routing</strong> — the shared {@link MenuListener} recognises our menus by the
 *       {@link MenuHolder} and invokes the {@link ClickAction} the render bound to the clicked slot;</li>
 *   <li><strong>re-render</strong> — {@link #reopen} re-renders the current state and re-opens it (the
 *       pagination / drill-down primitive).</li>
 * </ul>
 *
 * <p>A menu implements only {@link #render}: populate the holder's inventory with icons and bind a
 * {@link ClickAction} to each interactive slot for the holder's current navigation state. Display-only
 * menus bind no actions (the listener cancels every click regardless, so nothing can ever be moved).
 */
public interface Menu {

    /** The registry name this menu is opened by (e.g. {@code /se menu <name>}); lower-cased on registration. */
    String name();

    /**
     * An extra permission node required to open this menu, or {@code null} to inherit the blanket
     * {@code starenchants.admin} gate on {@code /se} (the default for the player-facing menus). Admin-only
     * menus (e.g. the grant browser) return a distinct node.
     */
    default String permission() {
        return null;
    }

    /**
     * Render the menu for the holder's current navigation state: call {@link MenuHolder#begin} to create the
     * inventory, then {@link MenuHolder#set} for each icon (+ optional {@link ClickAction}). Pure aside from
     * allocating the inventory — safe to call off the player's thread (the {@code open} below schedules the
     * actual {@code openInventory}).
     */
    void render(MenuHolder holder);

    /** Open this menu fresh (page 0, no sub-view) for {@code player}, hopping to the player's region thread. */
    default void open(Player player) {
        MenuHolder holder = new MenuHolder(this);
        render(holder);
        Scheduling.onEntity(player, () -> player.openInventory(holder.getInventory()));
    }

    /**
     * Re-render the holder's (already mutated) navigation state and re-open it on the player's thread — the
     * primitive behind page turns, drill-down and back. Schedule the open (don't call {@code openInventory}
     * inline from the click handler) so the swap lands on a clean tick — Folia-safe.
     */
    default void reopen(MenuClick click) {
        render(click.holder());
        Scheduling.onEntity(click.player(), () -> click.player().openInventory(click.holder().getInventory()));
    }
}
