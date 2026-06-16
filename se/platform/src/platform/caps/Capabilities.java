package platform.caps;

import java.util.Objects;
import org.bukkit.Server;

/**
 * The boot-time platform probe (docs/architecture.md §9): an immutable snapshot of the
 * few facts the rest of the plugin must branch on — is this <em>Folia</em> (no single main
 * thread, region-sharded; see the {@code folia-scheduling} skill), and which Minecraft
 * version is this, so the floor build can gate the {@code compat-folia}/{@code compat-modern}
 * edges behind a capability instead of a brittle version-string {@code if}.
 *
 * <p>This is a <em>domain-free</em> leaf with no compile dependency on Folia or any
 * newer-than-floor API: Folia is detected by a class probe, the version by parsing the
 * server's reported string. Everything downstream consults the resulting flags
 * ({@link #folia()}, {@link #atLeast(int, int, int)}) — never the raw server again.
 *
 * <p>The version-parsing core ({@link #parseVersion(String)}) is pure and unit-tested; the
 * {@link #probe(Server)} factory is the only part that touches Bukkit, so the matrix is the
 * only place the live probe is exercised.
 */
public final class Capabilities {

    /** The canonical Folia marker class, present only on a threaded-regions server. */
    static final String FOLIA_MARKER = "io.papermc.paper.threadedregions.RegionizedServer";

    private final boolean folia;
    private final int major;
    private final int minor;
    private final int patch;

    Capabilities(boolean folia, int major, int minor, int patch) {
        this.folia = folia;
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    /**
     * Probe a live server: detect Folia by the threaded-regions marker class and read the
     * Minecraft version from {@link Server#getBukkitVersion()} (e.g. {@code 1.20.6-R0.1-SNAPSHOT}).
     */
    public static Capabilities probe(Server server) {
        Objects.requireNonNull(server, "server");
        return probe(server.getBukkitVersion(), foliaPresent());
    }

    /** Probe from an explicit version string + Folia flag — the seam unit tests drive. */
    public static Capabilities probe(String bukkitVersion, boolean folia) {
        int[] v = parseVersion(bukkitVersion);
        return new Capabilities(folia, v[0], v[1], v[2]);
    }

    /** Whether the Folia threaded-regions runtime is present on the classpath. */
    public static boolean foliaPresent() {
        try {
            Class.forName(FOLIA_MARKER);
            return true;
        } catch (ClassNotFoundException notFolia) {
            return false;
        }
    }

    /**
     * Parse {@code major.minor[.patch]} out of a Bukkit version string, tolerating the
     * {@code -R0.1-SNAPSHOT} suffix and a missing patch (which Mojang omits on {@code x.y.0}
     * releases, e.g. {@code 1.21}). Returns {@code {major, minor, patch}}; an unparseable
     * leading number yields {@code {0, 0, 0}} so a probe never throws on a surprise format.
     */
    public static int[] parseVersion(String bukkitVersion) {
        int[] out = {0, 0, 0};
        if (bukkitVersion == null || bukkitVersion.isEmpty()) {
            return out;
        }
        // Keep only the leading "1.20.6" portion: stop at the first char that is not a
        // digit or a dot (the "-R0.1-SNAPSHOT" suffix, or a pre-release marker).
        int end = 0;
        while (end < bukkitVersion.length()) {
            char c = bukkitVersion.charAt(end);
            if ((c >= '0' && c <= '9') || c == '.') {
                end++;
            } else {
                break;
            }
        }
        String core = bukkitVersion.substring(0, end);
        if (core.isEmpty()) {
            return out;
        }
        String[] parts = core.split("\\.");
        for (int i = 0; i < 3 && i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                try {
                    out[i] = Integer.parseInt(parts[i]);
                } catch (NumberFormatException ignored) {
                    // Leave this component at 0; a trailing dot or empty segment is benign.
                }
            }
        }
        return out;
    }

    /** True on a Folia (threaded-regions) server; false on Paper/Spigot. */
    public boolean folia() {
        return folia;
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public int patch() {
        return patch;
    }

    /** Inclusive lower-bound version test: is this server at least {@code major.minor.patch}? */
    public boolean atLeast(int major, int minor, int patch) {
        if (this.major != major) {
            return this.major > major;
        }
        if (this.minor != minor) {
            return this.minor > minor;
        }
        return this.patch >= patch;
    }

    /**
     * Whether this server is mojang-mapped — the 1.20.5 spigot&rarr;mojang flip. The compiled
     * runtime never needs the mappings (it only touches the Bukkit API), but cross-version
     * tooling and the reference cache do (see {@code paper-cross-version}).
     */
    public boolean mojangMapped() {
        return atLeast(1, 20, 5);
    }

    @Override
    public String toString() {
        return "Capabilities[" + major + "." + minor + "." + patch
                + (folia ? ", Folia" : ", Paper") + "]";
    }
}
