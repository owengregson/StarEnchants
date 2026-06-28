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
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.diag.Source;

/**
 * Loads a whole {@code content/} tree into a {@link Library} (ADR-0014, ADR-0016): walk each source-typed
 * directory, read each {@code .yml} into its {@code AbilityDef}s + metadata, then run the injected
 * {@link Compiler} once over all defs. Pure (no server) so it is reused verbatim by {@code validateContent}.
 * Files are visited in sorted order so dense ability ids are deterministic; every content fault is a
 * {@code file:line:col} diagnostic, and the load throws only on a genuine I/O failure walking the tree.
 *
 * <p>The tier subfolder ({@code enchants/mythic/x.yml}) is NOT part of the stable key
 * ({@code <source>/<filename>}), so re-tiering a file never changes the PDC-stamped identity of live gear;
 * an in-file {@code tier:} overrides the folder. Two files yielding the same key are an
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
        validateRelationships(catalog, diags); // §G: requires/blacklist must name existing enchants
        validateSetEnchants(sets, catalog, diags); // §6.6: a set's custom enchant refs must exist (in range)
        Snapshot snapshot = compiler.compile(defs, generation, diags);
        return new Library(snapshot, catalog, crystals, sets, tiers, diags.all());
    }

    /**
     * Whole-library referential-integrity pass (§G): every {@code requires}/{@code blacklist} must name an
     * existing enchant. Otherwise a typo'd {@code requires} silently blocks the enchant at apply time and a
     * typo'd {@code blacklist} silently never matches — both become blocking diagnostics here. Must run after
     * the catalog is fully built; a per-file reader cannot see other keys.
     */
    private static void validateRelationships(List<EnchantDef> catalog, Diagnostics diags) {
        Set<String> keys = new HashSet<>();
        for (EnchantDef def : catalog) {
            keys.add(def.key());
        }
        for (EnchantDef def : catalog) {
            for (String req : def.requires()) {
                if (!keys.contains(req)) {
                    diags.error(DiagCode.E_REL_UNKNOWN,
                            "enchant '" + def.key() + "' requires unknown enchant '" + req + "'",
                            def.source(), "the requires: key must name an existing enchant");
                }
            }
            for (String black : def.blacklist()) {
                if (!keys.contains(black)) {
                    diags.error(DiagCode.E_REL_UNKNOWN,
                            "enchant '" + def.key() + "' blacklists unknown enchant '" + black + "'",
                            def.source(), "the blacklist: key must name an existing enchant");
                }
            }
        }
    }

    /**
     * Whole-library check (§6.6) that every set's CUSTOM enchant ref ({@code enchants/<id>}) names an existing
     * enchant at a valid level. Vanilla enchant names (any key without the {@code enchants/} prefix) are
     * resolved cross-version at mint and skip-on-miss, so they are not checked here. Mirrors
     * {@link #validateRelationships}: a typo'd custom ref would otherwise silently mint a piece missing its
     * enchant. Must run after the catalog is fully built.
     */
    private static void validateSetEnchants(List<SetDef> sets, List<EnchantDef> catalog, Diagnostics diags) {
        java.util.Map<String, EnchantDef> byKey = new java.util.HashMap<>();
        for (EnchantDef def : catalog) {
            byKey.put(def.key(), def);
        }
        for (SetDef set : sets) {
            checkSetEnchantRefs(set, set.armorEnchants(), byKey, diags);
            checkSetEnchantRefs(set, set.weaponEnchants(), byKey, diags);
        }
    }

    private static void checkSetEnchantRefs(SetDef set, java.util.Map<String, Integer> enchants,
            java.util.Map<String, EnchantDef> byKey, Diagnostics diags) {
        for (java.util.Map.Entry<String, Integer> entry : enchants.entrySet()) {
            String ref = entry.getKey();
            if (!ref.startsWith("enchants/")) {
                continue; // a vanilla enchant name — resolved at mint, not a library reference
            }
            EnchantDef def = byKey.get(ref);
            if (def == null) {
                diags.error(DiagCode.E_SET_ENCHANT_UNKNOWN,
                        "set '" + set.key() + "' applies unknown custom enchant '" + ref + "'",
                        set.source(), "the enchants: key must name an existing enchant (enchants/<id>) or a vanilla enchant name");
            } else if (entry.getValue() < 1 || entry.getValue() > def.maxLevel()) {
                diags.error(DiagCode.E_SET_ENCHANT_LEVEL,
                        "set '" + set.key() + "' applies '" + ref + "' at level " + entry.getValue()
                                + " (valid 1.." + def.maxLevel() + ")", set.source());
            }
        }
    }

    /** A source file's tier-folder-stripped key and resolved folder tier. */
    private record KeyTier(String key, String tier) {
    }

    /** Derive a source file's stable key ({@code <source>/<stem>}) and folder tier (ADR-0016). */
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
            diags.error(DiagCode.E_DUPLICATE_KEY, "two content files resolve to the same key '" + key
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
