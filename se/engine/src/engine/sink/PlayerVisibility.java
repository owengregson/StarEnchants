package engine.sink;

import org.bukkit.entity.Player;

/**
 * The sink's view of per-viewer player visibility ({@code VIEWER_HIDE}) — a seam because the modern call is
 * {@code hidePlayer(Plugin, Player)} and no engine class may hold a {@code Plugin} (that is what keeps the
 * engine composable and testable). Wired at the composition root from the era bindings, which do hold one.
 * Always called on the VIEWER's own thread: the hidden set belongs to their connection.
 */
@FunctionalInterface
public interface PlayerVisibility {

    /** The inert default for non-root construction sites (tests, tester suites): nobody is ever hidden. */
    PlayerVisibility NONE = (viewer, subject, visible) -> { };

    /** Show ({@code visible}) or hide {@code subject} for {@code viewer}. */
    void setVisible(Player viewer, Player subject, boolean visible);
}
