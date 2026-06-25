package pack;

import java.util.Objects;

/**
 * A config pack's {@code pack.yml} header (ADR-0023). Hand-rolled flat-YAML codec to stay dependency-free;
 * string values are always double-quoted on write so colour codes, colons, and quotes survive a round trip.
 */
public record PackManifest(String name, String description, String author, int format,
                           String created, int fileCount) {

    /** Bumped only on an incompatible archive-layout change. */
    public static final int CURRENT_FORMAT = 1;

    public static final String ENTRY = "pack.yml";

    public PackManifest {
        Objects.requireNonNull(name, "name");
        description = description == null ? "" : description;
        author = author == null ? "" : author;
        created = created == null ? "" : created;
        format = format <= 0 ? CURRENT_FORMAT : format;
        fileCount = Math.max(0, fileCount);
    }

    public static PackManifest of(String name, String description, String author, String created) {
        return new PackManifest(name, description, author, CURRENT_FORMAT, created, 0);
    }

    public PackManifest withFileCount(int count) {
        return new PackManifest(name, description, author, format, created, count);
    }

    public String toYaml() {
        return "# StarEnchants config pack (ADR-0023). Apply with /se pack apply " + name + "\n"
                + "name: " + quote(name) + "\n"
                + "description: " + quote(description) + "\n"
                + "author: " + quote(author) + "\n"
                + "format: " + format + "\n"
                + "created: " + quote(created) + "\n"
                + "files: " + fileCount + "\n";
    }

    /** Parse a {@code pack.yml}; never throws so a damaged manifest still lists (under {@code fallbackName}). */
    public static PackManifest fromYaml(String yaml, String fallbackName) {
        String name = fallbackName;
        String description = "";
        String author = "";
        int format = CURRENT_FORMAT;
        String created = "";
        int fileCount = 0;
        for (String raw : yaml.split("\n", -1)) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).strip();
            String value = line.substring(colon + 1).strip();
            switch (key) {
                case "name" -> { String v = unquote(value); if (!v.isBlank()) { name = v; } }
                case "description" -> description = unquote(value);
                case "author" -> author = unquote(value);
                case "format" -> format = parseInt(unquote(value), CURRENT_FORMAT);
                case "created" -> created = unquote(value);
                case "files" -> fileCount = parseInt(unquote(value), 0);
                default -> { /* forward-compatible: ignore unknown keys */ }
            }
        }
        return new PackManifest(name, description, author, format, created, fileCount);
    }

    private static String quote(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            String inner = s.substring(1, s.length() - 1);
            return inner.replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return s;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
