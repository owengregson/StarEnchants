package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Drift guard for the docs-site "operator surface" catalog ({@code website/src/data/surface.json}, ADR-0028):
 * the {@code /se} commands, rarity tiers, item kinds, and the annotated {@code config.yml} — generated from the
 * real sources ({@link SeCommand#COMMANDS} + the bundled resources) so the docs can't drift from the plugin.
 * Regenerate with {@code -Dse.doc.regen=true} (or {@code ./gradlew regenDocs}).
 */
class SurfaceCatalogDriftTest {

    private static final String RELATIVE = "website/src/data/surface.json";

    @Test
    void committedSurfaceMatchesTheSources() throws IOException {
        Path root = repoRoot();
        String fresh = render(root);
        Path file = root.resolve(RELATIVE);

        if (Boolean.getBoolean("se.doc.regen")) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, fresh, StandardCharsets.UTF_8);
            System.out.println("[SurfaceCatalogDriftTest] regenerated " + file);
            return;
        }

        assertTrue(Files.isRegularFile(file),
                "missing " + RELATIVE + " — generate it with `./gradlew regenDocs`");
        assertEquals(Files.readString(file, StandardCharsets.UTF_8), fresh,
                RELATIVE + " drifted from the sources (commands / tiers / items / config.yml) — "
                        + "regenerate with `./gradlew regenDocs`");
    }

    private static String render(Path root) throws IOException {
        Path res = root.resolve("se/bootstrap/resources");
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"version\": 1,\n");

        sb.append("  \"commands\": [\n");
        for (int i = 0; i < SeCommand.COMMANDS.size(); i++) {
            CommandInfo c = SeCommand.COMMANDS.get(i);
            sb.append("    { \"name\": ").append(q(c.name()))
                    .append(", \"args\": ").append(q(c.args()))
                    .append(", \"description\": ").append(q(c.description()))
                    .append(", \"alias\": ").append(c.alias()).append(" }")
                    .append(i < SeCommand.COMMANDS.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ],\n");

        Map<String, Object> tiersRoot = load(res.resolve("content/tiers.yml"));
        Map<String, Object> tiers = asMap(tiersRoot.get("tiers"));
        sb.append("  \"tiers\": {\n");
        sb.append("    \"default\": ").append(q(String.valueOf(tiersRoot.get("default-tier")))).append(",\n");
        sb.append("    \"list\": [\n");
        int ti = 0;
        for (Map.Entry<String, Object> e : tiers.entrySet()) {
            Map<String, Object> t = asMap(e.getValue());
            sb.append("      { \"id\": ").append(q(e.getKey()))
                    .append(", \"color\": ").append(q(String.valueOf(t.get("color"))))
                    .append(", \"weight\": ").append(numberOf(t.get("weight")))
                    .append(", \"glint\": ").append(Boolean.TRUE.equals(t.get("glint"))).append(" }")
                    .append(++ti < tiers.size() ? ",\n" : "\n");
        }
        sb.append("    ]\n  },\n");

        List<Path> itemFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.list(res.resolve("items"))) {
            stream.filter(p -> p.getFileName().toString().endsWith(".yml")).sorted().forEach(itemFiles::add);
        }
        sb.append("  \"items\": [\n");
        for (int i = 0; i < itemFiles.size(); i++) {
            Path p = itemFiles.get(i);
            Map<String, Object> m = load(p);
            String key = p.getFileName().toString().replaceFirst("\\.yml$", "");
            sb.append("    { \"key\": ").append(q(key))
                    .append(", \"type\": ").append(quoteOrNull(m.get("type")))
                    .append(", \"material\": ").append(quoteOrNull(m.get("material")))
                    .append(", \"name\": ").append(quoteOrNull(m.get("name"))).append(" }")
                    .append(i < itemFiles.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ],\n");

        // The annotated config.yml verbatim — drift-free and keeps its inline comments for the docs.
        sb.append("  \"config\": ").append(q(Files.readString(res.resolve("config.yml"), StandardCharsets.UTF_8)));
        sb.append("\n}\n");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(Path p) throws IOException {
        try (var in = Files.newInputStream(p)) {
            Object o = new Yaml().load(in);
            return o instanceof Map ? (Map<String, Object>) o : Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    /** A whole number where the YAML had one, else 0 — never a quoted string, so it stays a JSON number. */
    private static String numberOf(Object o) {
        return o instanceof Number n ? Long.toString(n.longValue()) : "0";
    }

    private static String quoteOrNull(Object o) {
        return o == null ? "null" : q(String.valueOf(o));
    }

    private static String q(String s) {
        StringBuilder b = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the repo root (no settings.gradle.kts found)");
    }
}
