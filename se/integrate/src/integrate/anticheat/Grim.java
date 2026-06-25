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
 * GrimAC compatibility (docs/decisions/0027). Grim is prediction-based with no per-action exemption, so blunt
 * check-disabling would weaken it; instead, cancel a {@code FlagEvent} only for a player StarEnchants itself
 * just moved within a tight window — every other check stays active. Catches the residual edge where engine
 * motion (a {@code KNOCKBACK_CONTROL}-corrected knockback, a sudden launch/teleport; ADR 0026) briefly defeats
 * prediction.
 */
final class Grim {

    private static final long WINDOW_MILLIS = 600L; // ~12 ticks: tight enough to bound the exploit surface

    private final Map<String, Long> recentMotionUntil = new ConcurrentHashMap<>();

    private Grim() {
    }

    /**
     * Subscribe the flag-cancellation listener and return the "just moved this player" recorder to fold into
     * the movement-exemption hook.
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
