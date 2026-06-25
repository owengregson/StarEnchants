package feature.imports;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * The {@code SE1} import codec (ADR-0029) — the wire contract shared byte-for-byte with the web creator.
 * A code is {@code SE1:} + base64url-nopad of zlib-deflated UTF-8 of a JSON/YAML envelope
 * {@code {v, kind, key, content}}. The compression is the standard zlib wrapper (header + adler32), which
 * is what {@code pako.deflate} (JS) emits and {@link Inflater}/{@link Deflater} in their default (non-raw)
 * mode read/write — that mutual default is the whole reason the two sides agree.
 *
 * <p>Pure logic (no Bukkit): {@link #decode} parses + sanitizes, {@link #encode} is its inverse (used by
 * the unit round-trip). YAML is a JSON superset, so the JSON envelope parses with SnakeYAML — no new JSON
 * dependency (ADR-0029). The {@code key} is sanitized to {@code [a-z0-9-]+} on decode and REJECTED
 * otherwise, so a hostile code can never escape {@code content/enchants/} (no path traversal).
 */
public final class ImportCode {

    /** Format tag: StarEnchants import, version 1. An unknown prefix is a friendly decode error. */
    public static final String PREFIX = "SE1:";

    /** The only {@code key} shape the format accepts → a safe filename stem; anything else is rejected. */
    private static final Pattern SAFE_KEY = Pattern.compile("[a-z0-9-]+");

    /** A wildly oversized inflate is a malformed/hostile code, not a real enchant — cap it (zip-bomb guard). */
    private static final int MAX_INFLATED_BYTES = 1 << 20; // 1 MiB

    private ImportCode() {
    }

    /** A decoded {@code SE1} envelope. {@code content} is the on-disk enchant def map (ADR-0029). */
    public record Envelope(int v, String kind, String key, Map<String, Object> content) {
    }

    /** Thrown for any malformed/old/hostile code; the message is operator-facing (shown verbatim). */
    public static final class DecodeException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public DecodeException(String message) {
            super(message);
        }

        public DecodeException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Decode and validate a paste code. Rejects (with a clear message) a wrong prefix, bad base64, a
     * corrupt zlib stream, a non-object/wrong-shape envelope, the wrong {@code v}/{@code kind}, or an
     * unsafe {@code key}. Never returns a partially-trusted envelope.
     */
    public static Envelope decode(String code) {
        if (code == null) {
            throw new DecodeException("no import code given");
        }
        String trimmed = code.strip();
        if (!trimmed.startsWith(PREFIX)) {
            throw new DecodeException("not a StarEnchants import code (must start with " + PREFIX + ")");
        }
        String payload = trimmed.substring(PREFIX.length());
        byte[] compressed;
        try {
            compressed = Base64.getUrlDecoder().decode(payload); // url-safe alphabet, no-pad accepted
        } catch (IllegalArgumentException bad) {
            throw new DecodeException("the code is not valid base64url (corrupted in transit?)", bad);
        }
        byte[] json = inflate(compressed);
        Object parsed = new Yaml().load(new String(json, StandardCharsets.UTF_8)); // JSON ⊂ YAML
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new DecodeException("the code's payload is not an object envelope");
        }
        int v = intField(map, "v");
        if (v != 1) {
            throw new DecodeException("unsupported import version " + v + " (this server reads v1)");
        }
        String kind = stringField(map, "kind");
        if (!"enchant".equals(kind)) {
            throw new DecodeException("unsupported import kind '" + kind + "' (v1 imports enchants only)");
        }
        String key = sanitizeKey(stringField(map, "key"));
        Object content = map.get("content");
        if (!(content instanceof Map<?, ?> contentMap)) {
            throw new DecodeException("the envelope has no 'content' object");
        }
        return new Envelope(v, kind, key, copyStringKeyed(contentMap));
    }

    /**
     * Render a decoded {@code content} map to the on-disk YAML written to {@code content/enchants/<key>.yml}.
     * Block style for readability; the per-level {@code effects:} sequence stays flow ({@code - { POTION: … }})
     * to match the shipped layout (e.g. {@code frostbite.yml}). The result re-parses through the same loader
     * {@code /se reload} uses, so this is the format the import validates and ships.
     */
    public static String toYaml(Map<String, Object> content) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        return new Yaml(options).dump(content);
    }

    /** Encode an envelope back into a paste code — the exact inverse of {@link #decode} (round-trip tested). */
    public static String encode(Envelope envelope) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("v", envelope.v());
        root.put("kind", envelope.kind());
        root.put("key", envelope.key());
        root.put("content", envelope.content());
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW); // compact JSON-ish so the code stays short
        byte[] json = new Yaml(options).dump(root).getBytes(StandardCharsets.UTF_8);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(deflate(json));
    }

    /**
     * Validate + normalize a content key to a safe filename stem (ADR-0029). Lowercased first so the web
     * creator's casing is forgiving, then matched against {@code [a-z0-9-]+}. A key that escapes (slashes,
     * dots, {@code ..}, absolute paths) is rejected — there is no path traversal off {@code content/enchants/}.
     */
    public static String sanitizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new DecodeException("the envelope has no 'key'");
        }
        String normalized = key.strip().toLowerCase(java.util.Locale.ROOT);
        if (!SAFE_KEY.matcher(normalized).matches()) {
            throw new DecodeException("unsafe enchant key '" + key + "' (allowed: lowercase letters, digits, '-')");
        }
        return normalized;
    }

    private static byte[] inflate(byte[] compressed) {
        Inflater inflater = new Inflater(); // default = zlib wrapper, the mate of pako.deflate / Deflater default
        inflater.setInput(compressed);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, compressed.length * 3));
        byte[] buffer = new byte[8192];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        throw new DecodeException("the code's compressed payload is truncated or corrupt");
                    }
                    continue;
                }
                out.write(buffer, 0, n);
                if (out.size() > MAX_INFLATED_BYTES) {
                    throw new DecodeException("the import payload is implausibly large");
                }
            }
        } catch (DataFormatException bad) {
            throw new DecodeException("the code is not valid zlib data (wrong/old format?)", bad);
        } finally {
            inflater.end();
        }
        return out.toByteArray();
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION); // default = zlib wrapper (mate of pako)
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, data.length / 2));
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return out.toByteArray();
    }

    private static int intField(Map<?, ?> map, String field) {
        Object value = map.get(field);
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new DecodeException("the envelope's '" + field + "' is missing or not a number");
    }

    private static String stringField(Map<?, ?> map, String field) {
        Object value = map.get(field);
        if (value instanceof String s) {
            return s;
        }
        throw new DecodeException("the envelope's '" + field + "' is missing or not a string");
    }

    /** Copy the content map keyed by String, rejecting a non-String key (a structurally-wrong def). */
    private static Map<String, Object> copyStringKeyed(Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String k)) {
                throw new DecodeException("the content has a non-text key: " + entry.getKey());
            }
            out.put(k, entry.getValue());
        }
        return out;
    }
}
