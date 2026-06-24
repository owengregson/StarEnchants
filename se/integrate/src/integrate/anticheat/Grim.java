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
 * GrimAC compatibility (docs/decisions/0027) — the {@code Mental + StarEnchants + GrimAC} combo in
 * particular. Grim is a prediction-based anti-cheat with no per-action exemption (by design it uses setbacks,
 * not exemptions); blunt check-disabling would weaken it. Instead this is <em>surgical</em>: it subscribes to
 * Grim's own {@code FlagEvent} through the GrimAPI {@code EventBus} and cancels a flag <strong>only</strong>
 * for a player StarEnchants itself just moved (a {@code VELOCITY} / {@code TELEPORT} effect), within a tight
 * window — so an engine-applied motion Grim can't predict never produces a false flag, while every other
 * check stays fully active.
 *
 * <p><b>Why this shape.</b> SE applies velocity/teleport through the server events Grim already predicts, and
 * the Mental+SE knockback combo delivers its final vector through Mental's authoritative pipeline (ADR 0026)
 * that Grim verifies against — so most motion needs nothing. This catches the residual edge (a
 * {@code KNOCKBACK_CONTROL}-corrected pre-delivered knockback, a sudden engine launch/teleport) where
 * prediction can briefly disagree.
 *
 * <p>Compiled against the real GrimAPI ({@code FlagEvent}, the {@code EventBus}), so a renamed/removed
 * accessor is a compile error here. Loaded only when GrimAC is present (gated by the registrar), so the core
 * never needs the Grim classes on a server without it.
 */
final class Grim {

    /** How long after an SE-applied motion a Grim flag for that player is treated as engine-caused. */
    private static final long WINDOW_MILLIS = 600L; // ~12 ticks: tight enough to bound the exploit surface

    private final Map<String, Long> recentMotionUntil = new ConcurrentHashMap<>();

    private Grim() {
    }

    /**
     * Subscribe the Grim flag-cancellation listener (via the GrimAPI EventBus) and return the "StarEnchants
     * just moved this player" recorder to fold into the anti-cheat movement-exemption hook. Called by the
     * registrar only when GrimAC is present, so referencing this class (and the Grim API) is gated.
     */
    @SuppressWarnings("deprecation") // the class-keyed subscribe is the simplest stable form, kept by GrimAPI
    static Consumer<Player> install(Plugin plugin, System.Logger log) {
        Grim grim = new Grim();
        // getAsync resolves once Grim's API is published (robust to load order); subscribe FlagEvent then.
        GrimAPIProvider.getAsync().thenAccept(api ->
                api.getEventBus().subscribe(plugin, FlagEvent.class, grim::onFlag));
        log.log(System.Logger.Level.INFO, "anti-cheat: GrimAC flag-cancellation active for engine-applied"
                + " movement (VELOCITY/TELEPORT); the Mental+StarEnchants knockback combo delivers through"
                + " Mental's authoritative pipeline that Grim predicts natively (ADR 0026).");
        return grim::record;
    }

    /** Mark that StarEnchants just applied movement to {@code player}. */
    void record(Player player) {
        recentMotionUntil.put(player.getName().toLowerCase(Locale.ROOT), System.currentTimeMillis() + WINDOW_MILLIS);
    }

    /** GrimAPI {@code GrimEventListener<FlagEvent>}: cancel a flag StarEnchants' own movement just caused. */
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
