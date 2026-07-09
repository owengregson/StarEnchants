package feature.scroll;

import feature.compat.Hands;
import feature.compat.Mats;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

/**
 * Stops the default-likeness Holy White Scroll — a genuine {@code TOTEM_OF_UNDYING}
 * (items/holy-white-scroll.yml) — from doubling as a working vanilla totem (F23). Vanilla totem activation is
 * material-only, so a scroll in hand would cancel the death outright and be consumed as a totem, far beyond the
 * scroll's design (keep ONE marked item through a normal death). This capability-probes
 * {@code EntityResurrectEvent} and, when a holy scroll is the totem about to pop, cancels the resurrection so
 * death proceeds and the scroll's real keep-on-death path applies instead.
 *
 * <p>Capability-probed like {@link feature.combat.MentalKnockbackBridge}: the event class is absent on 1.8.9
 * (no totems at all) → {@link Path#ABSENT}, nothing registered, and the executor's hand reads never link there.
 *
 * <p>Edge: a holy scroll in the MAIN hand with a REAL totem in the off hand — vanilla would consume the
 * main-hand item, we cancel, and the player dies despite owning a real totem (we cannot redirect consumption
 * without NMS). Rare and self-inflicted.
 */
public final class HolyTotemGuard {

    /** Outcome of {@link #register} — for the boot log / verification. */
    public enum Path {
        /** Hooked {@code EntityResurrectEvent}; holy-scroll totem pops are suppressed. */
        BOUND,
        /** No {@code EntityResurrectEvent} on this version (1.8.9) — there are no totems to guard. */
        ABSENT
    }

    /** The totem-pop event, present iff this version has totems (1.9+). Probed by name so 1.8 stays loadable. */
    public static final String RESURRECT_EVENT = "org.bukkit.event.entity.EntityResurrectEvent";

    private HolyTotemGuard() {
    }

    /**
     * Hook {@code EntityResurrectEvent} so a holy scroll never pops as a vanilla totem. A no-op returning
     * {@link Path#ABSENT} on a version without totems. {@code ignoreCancelled} also skips Paper's
     * fires-even-without-a-totem pre-cancelled case.
     */
    public static Path register(Plugin plugin, HolyScrollService service, Hands hands) {
        final Class<? extends Event> eventClass;
        try {
            eventClass = Class.forName(RESURRECT_EVENT).asSubclass(Event.class);
        } catch (ClassNotFoundException absent) {
            return Path.ABSENT; // no totems on this version — nothing to guard
        }
        EventExecutor executor = (ignored, event) -> handle(event, service, hands);
        plugin.getServer().getPluginManager().registerEvent(
                eventClass, new Listener() { }, EventPriority.NORMAL, executor, plugin, true);
        return Path.BOUND;
    }

    /** Cancel the resurrection when the popping totem is a holy scroll; leave any other totem (or mob) alone. */
    private static void handle(Event event, HolyScrollService service, Hands hands) {
        if (!(event instanceof EntityEvent entityEvent) || !(entityEvent.getEntity() instanceof Player player)) {
            return; // mob resurrects short-circuit — a mob holding a minted scroll is negligible
        }
        if (shouldCancel(player, hands, service) && event instanceof Cancellable cancellable) {
            cancellable.setCancelled(true);
        }
    }

    /**
     * The totem vanilla would pop for {@code player} — main hand first, then off hand (its
     * {@code checkTotemDeathProtection} order); {@code null} if neither hand holds a real totem.
     */
    static ItemStack poppingTotem(Player player, Hands hands) {
        Material totem = Mats.of("TOTEM_OF_UNDYING"); // null on 1.8 — no totem material, so nothing pops
        if (totem == null) {
            return null;
        }
        ItemStack main = hands.mainHand(player);
        if (main != null && main.getType() == totem) {
            return main;
        }
        ItemStack off = hands.offHand(player);
        if (off != null && off.getType() == totem) {
            return off;
        }
        return null;
    }

    /** Whether the totem about to pop for {@code player} is a holy scroll (so the pop must be cancelled). */
    static boolean shouldCancel(Player player, Hands hands, HolyScrollService service) {
        ItemStack totem = poppingTotem(player, hands);
        return totem != null && service.isHolyScroll(totem);
    }
}
