package compile.load;

import compile.Compiler;
import compile.def.AbilityDef;
import compile.model.Snapshot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import schema.diag.Diagnostics;
import schema.diag.Source;

/**
 * Loads a whole {@code content/} tree into a {@link Library} (ADR-0014, ADR-0016): walk each
 * source-typed directory, read every {@code .yml} into its {@code AbilityDef}s + metadata, then run the
 * injected {@link Compiler} once over all the defs to produce the immutable {@link Snapshot}. Pure —
 * file I/O + SnakeYAML + the existing grammar/compiler — so it is reused verbatim by
 * {@code validateContent} and is fully unit-testable with zero server.
 *
 * <p>Files are visited in sorted order so dense ability ids are assigned deterministically. Every
 * fault (unreadable file, malformed YAML, bad field) is a {@code file:line:col} diagnostic; the load
 * never throws on content, only on a genuine I/O failure walking the tree.
 *
 * <p><strong>Tier folders (ADR-0016).</strong> Source directories may be organised into tier
 * subfolders ({@code enchants/mythic/thunderstrike.yml}). The tier subfolder is NOT part of the stable
 * key — the key is {@code <source>/<filename>} ({@code enchants/thunderstrike}) regardless of folder —
 * so moving/re-tiering a file never changes the PDC-stamped identity of live gear. The tier is derived
 * from the immediate subfolder when it is a registered tier (else the registry default), and an in-file
 * {@code tier:} overrides it. Two files yielding the same key (same filename across tiers) are an
 * {@code E_DUPLICATE_KEY} error. Tiers come from {@code content/tiers.yml} ({@link TierRegistry}).
 */
public final class LibraryLoader {

    private static final String YML = ".yml";
    private static final String YAML = ".yaml";

    private LibraryLoader() {
    }

    /**
     * Load {@code contentRoot} into a {@link Library} using {@code compiler}, stamping {@code generation}.
     *
     * @param contentRoot the directory holding {@code enchants/}, …; may not exist (→ an empty library)
     * @param compiler    the wired compiler (effect specs + selectors + resolvers + trigger vocabulary)
     * @param generation  the build counter to stamp into the snapshot (§5.2)
     */
    public static Library load(Path contentRoot, Compiler compiler, int generation) {
        Diagnostics diags = new Diagnostics();
        TierRegistry tiers = loadTiers(contentRoot, diags);
        List<EnchantDef> catalog = new ArrayList<>();
        List<CrystalDef> crystals = new ArrayList<>();
        List<SetDef> sets = new ArrayList<>();
        List<ItemDef> items = new ArrayList<>();
        List<AbilityDef> defs = new ArrayList<>();
        int[] nextDefId = {0};
        Set<String> seenKeys = new HashSet<>();

        for (Path file : sourceFiles(contentRoot, "enchants")) {
            KeyTier kt = keyTierOf(contentRoot, "enchants", file, tiers);
            if (!claim(kt.key(), contentRoot, file, seenKeys, diags)) {
                continue;
            }
            YamlNode root = composeOf(contentRoot, file, diags);
            if (root == null) {
                continue;
            }
            EnchantDefReader.Parsed parsed =
                    EnchantDefReader.read(kt.key(), kt.tier(), root, () -> nextDefId[0]++, diags);
            if (parsed.def() != null) {
                catalog.add(parsed.def());
            }
            defs.addAll(parsed.abilities());
        }
        for (Path file : sourceFiles(contentRoot, "crystals")) {
            KeyTier kt = keyTierOf(contentRoot, "crystals", file, tiers);
            if (!claim(kt.key(), contentRoot, file, seenKeys, diags)) {
                continue;
            }
            YamlNode root = composeOf(contentRoot, file, diags);
            if (root == null) {
                continue;
            }
            CrystalDefReader.Parsed parsed =
                    CrystalDefReader.read(kt.key(), kt.tier(), root, () -> nextDefId[0]++, diags);
            if (parsed.def() != null) {
                crystals.add(parsed.def());
            }
            defs.addAll(parsed.abilities());
        }
        for (Path file : sourceFiles(contentRoot, "sets")) {
            KeyTier kt = keyTierOf(contentRoot, "sets", file, tiers);
            if (!claim(kt.key(), contentRoot, file, seenKeys, diags)) {
                continue;
            }
            YamlNode root = composeOf(contentRoot, file, diags);
            if (root == null) {
                continue;
            }
            SetDefReader.Parsed parsed =
                    SetDefReader.read(kt.key(), kt.tier(), root, () -> nextDefId[0]++, diags);
            if (parsed.def() != null) {
                sets.add(parsed.def());
            }
            defs.addAll(parsed.abilities());
        }
        // Carrier items: the key keeps the full path (items/<kind>/<name>) — items are not stamped on gear
        // as enchant keys, so there is no key-stability concern; the in-file `tier:` sets the tier.
        for (Path file : sourceFiles(contentRoot, "items")) {
            String key = stripExtension(relativePath(contentRoot, file));
            if (!claim(key, contentRoot, file, seenKeys, diags)) {
                continue;
            }
            YamlNode root = composeOf(contentRoot, file, diags);
            if (root == null) {
                continue;
            }
            ItemDef def = ItemDefReader.read(key, tiers.defaultTier(), root, diags);
            if (def != null) {
                items.add(def);
            }
        }

        Snapshot snapshot = compiler.compile(defs, generation, diags);
        return new Library(snapshot, catalog, crystals, sets, items, tiers, diags.all());
    }

    /** The path-and-tier of a source file: a tier-folder-stripped key and the resolved folder tier. */
    private record KeyTier(String key, String tier) {
    }

    /**
     * Derive a source file's stable key and folder tier (ADR-0016). The key is {@code <source>/<stem>}
     * — the tier subfolder is NOT part of it. The folder tier is the immediate subfolder under the
     * source root when it is a registered tier, else the registry default.
     */
    private static KeyTier keyTierOf(Path contentRoot, String source, Path file, TierRegistry tiers) {
        String key = source + "/" + stripExtension(file.getFileName().toString());
        String tier = tiers.defaultTier();
        String relative = relativePath(contentRoot, file);             // e.g. enchants/mythic/x.yml
        String prefix = source + "/";
        String rest = relative.startsWith(prefix) ? relative.substring(prefix.length()) : relative;
        int slash = rest.indexOf('/');
        if (slash >= 0) {
            String firstSub = rest.substring(0, slash);
            if (tiers.isTier(firstSub)) {
                tier = firstSub;
            }
        }
        return new KeyTier(key, tier);
    }

    /** Claim {@code key}; a second file claiming the same key is an {@code E_DUPLICATE_KEY} error (skip it). */
    private static boolean claim(String key, Path contentRoot, Path file, Set<String> seen, Diagnostics diags) {
        if (key == null || key.isEmpty() || key.endsWith("/")) {
            diags.error("load.key", "content file has no name stem: " + relativePath(contentRoot, file),
                    Source.ofFile(relativePath(contentRoot, file)));
            return false;
        }
        if (!seen.add(key)) {
            diags.error("E_DUPLICATE_KEY", "two content files resolve to the same key '" + key
                    + "' (a filename is reused across tier folders): " + relativePath(contentRoot, file),
                    Source.ofFile(relativePath(contentRoot, file)));
            return false;
        }
        return true;
    }

    /** Read {@code content/tiers.yml} into a {@link TierRegistry}; absent/unreadable → the built-in default. */
    private static TierRegistry loadTiers(Path contentRoot, Diagnostics diags) {
        Path tiersFile = contentRoot.resolve("tiers.yml");
        if (!Files.isRegularFile(tiersFile)) {
            return TierRegistry.BUILTIN;
        }
        String yaml = readFile(tiersFile, "tiers.yml", diags);
        if (yaml == null) {
            return TierRegistry.BUILTIN;
        }
        return TierRegistry.read(YamlNode.compose("tiers.yml", yaml, diags), diags);
    }

    /** The content files under {@code contentRoot/<dir>} in deterministic order, or empty if absent. */
    private static List<Path> sourceFiles(Path contentRoot, String dir) {
        Path sourceDir = contentRoot.resolve(dir);
        return Files.isDirectory(sourceDir) ? listContentFiles(contentRoot, sourceDir) : List.of();
    }

    /** Read + compose a content file into a {@link YamlNode}, or {@code null} (with a diagnostic) on I/O fault. */
    private static YamlNode composeOf(Path contentRoot, Path file, Diagnostics diags) {
        String label = relativePath(contentRoot, file);
        String yaml = readFile(file, label, diags);
        return yaml == null ? null : YamlNode.compose(label, yaml, diags);
    }

    /** The path under {@code contentRoot}, slash-normalised so keys/labels are stable cross-OS. */
    private static String relativePath(Path contentRoot, Path file) {
        return contentRoot.relativize(file).toString().replace('\\', '/');
    }

    /** Strip the exact {@code .yml}/{@code .yaml} suffix (not just the last dot) to get the base key. */
    private static String stripExtension(String relativePath) {
        if (relativePath.endsWith(YAML)) {
            return relativePath.substring(0, relativePath.length() - YAML.length());
        }
        if (relativePath.endsWith(YML)) {
            return relativePath.substring(0, relativePath.length() - YML.length());
        }
        return relativePath;
    }

    private static List<Path> listContentFiles(Path contentRoot, Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(LibraryLoader::isContentFile)
                    // Sort by the normalised relative-path STRING, not Path natural order: Path
                    // ordering is case-sensitivity-dependent (Linux vs macOS/Windows), which would
                    // assign dense ids differently in dev than on a Linux prod server.
                    .sorted(Comparator.comparing(p -> relativePath(contentRoot, p)))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("walking content directory " + dir, e);
        }
    }

    private static boolean isContentFile(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(YML) || name.endsWith(YAML);
    }

    private static String readFile(Path file, String label, Diagnostics diags) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            diags.error("load.io", "could not read content file: " + e.getMessage(), Source.ofFile(label));
            return null;
        }
    }
}
