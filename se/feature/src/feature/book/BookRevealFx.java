package feature.book;

import java.lang.reflect.Method;
import java.util.Map;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import platform.text.Colors;

/**
 * The unopened-book reveal fanfare (§I QOL): an instant, tier-coloured firework burst at the opener's feet plus
 * a brief subtitle naming the revealed book. Both are entity/player work, so call ONLY on the opener's own
 * region thread — the interact click already is (folia-scheduling), so no scheduler hop is needed.
 *
 * <p>Cross-version by REFLECTION, the same stance as {@code feature.compat.Sounds}: {@code Firework#detonate}
 * and the 5-arg {@code Player#sendTitle} exist across the whole modern floor (1.17.1+) but NOT on the 1.8.9
 * legacy lane, so a direct call would break the {@code -Pse.target=legacy} compile of one shared source. Looking
 * them up by name compiles on both lanes and lets 1.8 degrade gracefully — the firework bursts on its own
 * power-0 arc, and the subtitle falls back to the 2-arg {@code sendTitle} or is skipped.
 */
public final class BookRevealFx {

    /** Legacy {@code &} colour code → the firework burst colour (the 16 standard chat colours). */
    private static final Map<Character, Color> TIER_COLORS = Map.ofEntries(
            Map.entry('0', Color.fromRGB(0x000000)), Map.entry('1', Color.fromRGB(0x0000AA)),
            Map.entry('2', Color.fromRGB(0x00AA00)), Map.entry('3', Color.fromRGB(0x00AAAA)),
            Map.entry('4', Color.fromRGB(0xAA0000)), Map.entry('5', Color.fromRGB(0xAA00AA)),
            Map.entry('6', Color.fromRGB(0xFFAA00)), Map.entry('7', Color.fromRGB(0xAAAAAA)),
            Map.entry('8', Color.fromRGB(0x555555)), Map.entry('9', Color.fromRGB(0x5555FF)),
            Map.entry('a', Color.fromRGB(0x55FF55)), Map.entry('b', Color.fromRGB(0x55FFFF)),
            Map.entry('c', Color.fromRGB(0xFF5555)), Map.entry('d', Color.fromRGB(0xFF55FF)),
            Map.entry('e', Color.fromRGB(0xFFFF55)), Map.entry('f', Color.fromRGB(0xFFFFFF)));

    // A short, snappy subtitle (ticks) — the reveal is a flourish, not a lingering banner.
    private static final int FADE_IN = 5;
    private static final int STAY = 40;
    private static final int FADE_OUT = 10;

    /**
     * Celebrate a reveal at {@code player}: burst a firework tinted to {@code tierColorCode} (a legacy
     * {@code &}-code, e.g. {@code "&c&l"}) just above their feet and flash {@code bookName} (the styled book
     * likeness name) as a subtitle. Opener's region thread only.
     */
    public void celebrate(Player player, String tierColorCode, String bookName) {
        firework(player, colorFor(tierColorCode));
        subtitle(player, bookName == null ? "" : Colors.translate(bookName));
    }

    /** Spawn a plain (cosmetic, never crossbow-loaded → non-damaging) firework and detonate it at once. */
    private static void firework(Player player, Color color) {
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        Location at = player.getLocation().add(0, 0.2, 0); // just above the feet so the burst frames the player
        Firework firework = world.spawn(at, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.setPower(0);
        meta.addEffect(FireworkEffect.builder().withColor(color).with(FireworkEffect.Type.BURST).flicker(true).build());
        firework.setFireworkMeta(meta);
        detonate(firework);
    }

    /** Instant-detonate via reflection; the 1.8 lane (no {@code detonate}) leaves the power-0 rocket to burst on its own. */
    private static void detonate(Firework firework) {
        try {
            Method detonate = firework.getClass().getMethod("detonate");
            detonate.invoke(firework);
        } catch (ReflectiveOperationException | RuntimeException absent) {
            // no Firework#detonate on this version — graceful degrade (the burst still happens a tick later)
        }
    }

    /** Show {@code subtitle} with an empty title; degrade to the 2-arg overload, then to nothing, on old lanes. */
    private static void subtitle(Player player, String subtitle) {
        try {
            Method timed = player.getClass().getMethod("sendTitle",
                    String.class, String.class, int.class, int.class, int.class);
            timed.invoke(player, "", subtitle, FADE_IN, STAY, FADE_OUT);
            return;
        } catch (ReflectiveOperationException | RuntimeException absent) {
            // no 5-arg sendTitle — try the older 2-arg form below
        }
        try {
            Method basic = player.getClass().getMethod("sendTitle", String.class, String.class);
            basic.invoke(player, "", subtitle);
        } catch (ReflectiveOperationException | RuntimeException absent) {
            // 1.8.9 without either overload → skip the subtitle (graceful degrade)
        }
    }

    /** The burst colour for the FIRST colour code in {@code code} (formatting codes skipped), white if none. */
    private static Color colorFor(String code) {
        if (code != null) {
            for (int i = 0; i + 1 < code.length(); i++) {
                if (code.charAt(i) == '&') {
                    Color color = TIER_COLORS.get(Character.toLowerCase(code.charAt(i + 1)));
                    if (color != null) {
                        return color;
                    }
                }
            }
        }
        return Color.WHITE; // no colour code found → a neutral white burst
    }
}
