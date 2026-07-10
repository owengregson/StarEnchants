package feature.compat;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import org.bukkit.entity.Player;

/**
 * Cross-version title / subtitle / action-bar sending by REFLECTION (ADR-0044; the stance {@link Sounds} and the
 * old {@code BookRevealFx} established). The 5-arg {@code sendTitle} and the Spigot BungeeCord action-bar path
 * ({@code player.spigot().sendMessage(ChatMessageType.ACTION_BAR, …)}, the one action-bar path stable across the
 * whole modern floor) exist on 1.17.1+ but not the 1.8.9 lane, so looking them up by name compiles one shared
 * source on both lanes and lets 1.8 degrade gracefully (a shorter title overload, or a plain chat line).
 *
 * <p>Strings are expected ALREADY colour-translated (§, not &amp;) — {@code TextComponent.fromLegacyText} parses §.
 * All methods are player-region-thread calls (the caller must already be there); they never throw.
 */
public final class Titles {

    private Titles() {
    }

    /** Show {@code title}/{@code subtitle} with the given tick timings; degrade to the 2-arg overload then to nothing. */
    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player == null) {
            return;
        }
        String t = title == null ? "" : title;
        String s = subtitle == null ? "" : subtitle;
        try {
            Method timed = player.getClass().getMethod("sendTitle",
                    String.class, String.class, int.class, int.class, int.class);
            timed.invoke(player, t, s, fadeIn, stay, fadeOut);
            return;
        } catch (ReflectiveOperationException | RuntimeException absent) {
            // no 5-arg sendTitle — try the older 2-arg form below
        }
        try {
            Method basic = player.getClass().getMethod("sendTitle", String.class, String.class);
            basic.invoke(player, t, s);
        } catch (ReflectiveOperationException | RuntimeException absent) {
            // 1.8.9 without either overload → skip the title (graceful degrade)
        }
    }

    /** Show {@code subtitle} with an empty title. */
    public static void sendSubtitle(Player player, String subtitle, int fadeIn, int stay, int fadeOut) {
        sendTitle(player, "", subtitle, fadeIn, stay, fadeOut);
    }

    /**
     * Show {@code message} on the action bar via the Spigot BungeeCord chat API (reflected, so it links on neither
     * the legacy lane nor a stripped server). Degrades to a plain chat line where that API is absent (1.8.9).
     */
    public static void sendActionBar(Player player, String message) {
        if (player == null || message == null) {
            return;
        }
        try {
            Object spigot = player.getClass().getMethod("spigot").invoke(player);
            Class<?> chatType = Class.forName("net.md_5.bungee.api.ChatMessageType");
            Object actionBar = chatType.getField("ACTION_BAR").get(null);
            Class<?> baseComponent = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            Class<?> textComponent = Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Object components = textComponent.getMethod("fromLegacyText", String.class).invoke(null, message);
            Class<?> componentArray = Array.newInstance(baseComponent, 0).getClass();
            Method send = spigot.getClass().getMethod("sendMessage", chatType, componentArray);
            send.invoke(spigot, new Object[] {actionBar, components}); // wrap so the component[] is one arg, not spread
            return;
        } catch (ReflectiveOperationException | RuntimeException absent) {
            // no Spigot BungeeCord chat API (1.8.9 / a stripped server) → degrade to a plain chat line below
        }
        try {
            player.sendMessage(message);
        } catch (RuntimeException ignored) {
            // last-resort no-op
        }
    }
}
