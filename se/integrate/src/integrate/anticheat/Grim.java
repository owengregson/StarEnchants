package integrate.anticheat;

import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.event.events.FlagEvent;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * GrimAC compatibility (docs/decisions/0027). Grim is prediction-based with no per-action exemption (it uses
 * setbacks by design); blunt check-disabling would weaken it. So this is surgical: subscribe to Grim's
 * {@code FlagEvent} via the GrimAPI {@code EventBus} and cancel a flag only for a player StarEnchants itself
 * just moved (VELOCITY/TELEPORT) within a tight window — an engine motion Grim can't predict never produces a
 * false flag, every other check stays active.
 *
 * <p>Most motion needs nothing: SE applies velocity/teleport through the server events Grim already predicts,
 * and the Mental knockback combo delivers its vector through Mental's authoritative pipeline (ADR 0026) that
 * Grim verifies against. This catches the residual edge (a {@code KNOCKBACK_CONTROL}-corrected pre-delivered
 * knockback, a sudden engine launch/teleport) where prediction can briefly disagree.
 *
 * <p>Compiled against the real GrimAPI, so a renamed/removed accessor is a compile error here. Loaded only
 * when GrimAC is present (gated by the registrar), so the core never needs the Grim classes without it.
 */
final class Grim {

    /** How long after an SE-applied motion a Grim flag for that player is treated as engine-caused. */
    private static final long WINDOW_MILLIS = 600L; // ~12 ticks: tight enough to bound the exploit surface

    private final Map<String, Long> recentMotionUntil = new ConcurrentHashMap<>();

    private Grim() {
    }

    /**
     * Subscribe the flag-cancellation listener and return the "StarEnchants just moved this player" recorder
     * to fold into the movement-exemption hook. Called by the registrar only when GrimAC is present, so
     * referencing this class (and the Grim API) is gated.
     */
    @SuppressWarnings("deprecation") // class-keyed subscribe is the simplest stable form, kept by GrimAPI
    static Consumer<Player> install(Plugin plugin, System.Logger log) {
        Grim grim = new Grim();
        // getAsync resolves once Grim's API is published — robust to load order.
        GrimAPIProvider.getAsync().thenAccept(api ->
                api.getEventBus().subscribe(plugin, FlagEvent.class, grim::onFlag));
        log.log(System.Logger.Level.INFO, "anti-cheat: GrimAC flag-cancellation active for engine-applied"
                + " movement (VELOCITY/TELEPORT); the Mental+StarEnchants knockback combo delivers through"
                + " Mental's authoritative pipeline that Grim predicts natively (ADR 0026).");
        return grim::record;
    }

    void record(Player player) {
        recentMotionUntil.put(player.getName().toLowerCase(Locale.ROOT), System.currentTimeMillis() + WINDOW_MILLIS);
    }

    /** Cancel a flag StarEnchants' own movement just caused. */
    void onFlag(FlagEvent event) {
        if (event.getUser() == null) {
            return;
        }
        String name = event.getUser().getName();
        if (name != null && engineMovedRecently(name.toLowerCase(Locale.ROOT))) {
            event.setCancelled(true);
        }
    }

    private boolean engineMovedRecently(String name) {
        Long until = recentMotionUntil.get(name);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() > until) {
            recentMotionUntil.remove(name, until); // lazy eviction of an elapsed window
            return false;
        }
        return true;
    }
}
