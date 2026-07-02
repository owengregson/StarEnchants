package testfx;

/** The shared acceptance corpus for the two hardened SnakeYAML factories (compile.load.YamlNode and
 *  migrate.LegacyYaml) — one spec, two modules that cannot share code (migrate cannot reach compile), so the
 *  parity lives in these inputs + each module's acceptance test asserting the same outcomes. */
public final class YamlAcceptance {
    private YamlAcceptance() { }

    /** Duplicate key — must parse LAST-WINS (b: 2) in both factories. */
    public static final String DUP_KEY_DOC = "a: 1\nb: 1\nb: 2\n";

    /** N aliases of one collection anchor: within the 64 cap it parses; above it the parse fails. */
    public static String aliasDoc(int aliases) {
        StringBuilder b = new StringBuilder("base: &a [1, 2]\nitems:\n");
        for (int i = 0; i < aliases; i++) {
            b.append("  - *a\n");
        }
        return b.toString();
    }
}
