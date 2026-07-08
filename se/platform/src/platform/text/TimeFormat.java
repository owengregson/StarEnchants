package platform.text;

/**
 * Human-readable durations for likeness/message tokens ({@code {TIME_FORMATTED}}): {@code hms} renders a second
 * count as {@code "30s"} / {@code "2m 5s"} / {@code "1h 1m 4s"}, omitting zero units and never zero-padding, and
 * always showing at least seconds (a zero duration is {@code "0s"}). Pure — no Bukkit, unit-tested against
 * hand-computed expectations.
 */
public final class TimeFormat {
    private TimeFormat() { }

    /** {@code seconds} → {@code "1h 1m 4s"}, omitting zero units; non-positive → {@code "0s"}. */
    public static String hms(long seconds) {
        if (seconds <= 0) {
            return "0s";
        }
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder out = new StringBuilder();
        if (h > 0) {
            out.append(h).append('h');
        }
        if (m > 0) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(m).append('m');
        }
        if (s > 0) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(s).append('s');
        }
        return out.toString();
    }

    /** {@link #hms} of {@code ceil(ticks / 20)} seconds (20 ticks = 1s); rounds a partial second up. */
    public static String hmsFromTicks(long ticks) {
        return hms((long) Math.ceil(ticks / 20.0));
    }
}
