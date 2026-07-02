package feature.combat;

import org.bukkit.event.Listener;

/**
 * A do-nothing {@link Listener} (ADR-0044): the era bindings return {@link #INSTANCE} where a feeder's real work
 * is done off-event (the 1.8 ITEM_DAMAGE / heroic-save subscribers run inside {@code LegacyGearPoll}, not a
 * Bukkit event), so the composition root can register every era feeder uniformly. Registering it is inert.
 */
public final class NoopListener implements Listener {

    /** The shared inert listener instance. */
    public static final Listener INSTANCE = new NoopListener();

    private NoopListener() {
    }
}
