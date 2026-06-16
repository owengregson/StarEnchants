package migrate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import migrate.model.MigratedEffect;
import migrate.model.MigratedEnchant;
import migrate.model.MigratedLevel;
import migrate.model.MigratedSet;
import schema.diag.Diagnostics;
import schema.diag.Source;

/**
 * The EE/EA importer entry point (docs/architecture.md §10). Reads a legacy plugin's configs, maps them
 * to the unified vocabulary, and produces StarEnchants YAML keyed by output path — plus a
 * {@link Diagnostics} log of everything that needs manual attention (an unmapped trigger/applies, an
 * effect with no equivalent). The migration NEVER fails on an unmappable construct: it migrates the
 * structure and flags the gaps as warnings, so the operator gets a reviewable, mostly-complete tree.
 *
 * <p>v1 ships the EliteEnchantments and EliteArmor readers (the two plugins StarEnchants merges); the
 * model + {@link Mappings} + {@link SchemaWriter} are reader-agnostic, so an AdvancedEnchantments reader
 * is a single additional source class.
 */
public final class Migrator {

    /** The product of a migration: output files (relative path → YAML) and the review diagnostics. */
    public record Result(Map<String, String> files, Diagnostics diagnostics) {

        /**
         * Write every output file under {@code targetDir} (creating parent directories), returning how
         * many were written. Never overwrites an existing file — a re-run leaves an operator's edits
         * intact and reports the skip via the return count.
         */
        public int writeTo(java.nio.file.Path targetDir) throws java.io.IOException {
            int written = 0;
            java.nio.file.Path base = targetDir.normalize();
            for (Map.Entry<String, String> file : files.entrySet()) {
                java.nio.file.Path out = targetDir.resolve(file.getKey()).normalize();
                if (!out.startsWith(base)) {
                    continue; // defence-in-depth: never write outside the target (ids are already validated)
                }
                if (java.nio.file.Files.exists(out)) {
                    continue; // never clobber an existing (possibly hand-edited) file
                }
                java.nio.file.Files.createDirectories(out.getParent());
                java.nio.file.Files.writeString(out, file.getValue(), java.nio.charset.StandardCharsets.UTF_8);
                written++;
            }
            return written;
        }
    }

    /** Output ids must be plain content keys — no path separators or {@code ..}, which would escape the target. */
    private static final java.util.regex.Pattern SAFE_ID = java.util.regex.Pattern.compile("[A-Za-z0-9_-]+");

    private Migrator() {
    }

    /** Migrate an EliteEnchantments {@code enchantments.yml} into one {@code enchants/<id>.yml} per enchant. */
    public static Result eliteEnchantments(String enchantmentsYaml) {
        Map<String, String> files = new LinkedHashMap<>();
        Diagnostics diagnostics = new Diagnostics();
        for (MigratedEnchant enchant : EliteEnchantmentsReader.read(enchantmentsYaml)) {
            Source source = Source.ofFile("EliteEnchantments/enchantments.yml#" + enchant.id());
            if (!SAFE_ID.matcher(enchant.id()).matches()) {
                diagnostics.warning("migrate.id", "skipped enchant with unsafe id '" + enchant.id()
                        + "' (only letters/digits/_/- allowed)", source);
                continue;
            }
            if (enchant.trigger() == null) {
                diagnostics.warning("migrate.trigger", "enchant '" + enchant.id() + "': legacy type '"
                        + enchant.legacyTrigger() + "' has no StarEnchants trigger — set one manually", source);
            }
            if (enchant.appliesTo().isEmpty() && enchant.legacyApplies() != null) {
                diagnostics.warning("migrate.applies", "enchant '" + enchant.id() + "': legacy applies '"
                        + enchant.legacyApplies() + "' was not recognised — set applies-to manually", source);
            }
            warnUnmappedEffects(enchant.id(), enchantLevelsEffects(enchant), diagnostics, source);
            files.put("enchants/" + enchant.id() + ".yml", SchemaWriter.enchant(enchant));
        }
        return new Result(files, diagnostics);
    }

    /** Migrate one EliteArmor set file into a {@code sets/<id>.yml}. */
    public static Result eliteArmorSet(String id, String setYaml) {
        Map<String, String> files = new LinkedHashMap<>();
        Diagnostics diagnostics = new Diagnostics();
        Source source = Source.ofFile("EliteArmor/" + id + ".yml");
        if (!SAFE_ID.matcher(id).matches()) {
            diagnostics.warning("migrate.id", "skipped set with unsafe id '" + id
                    + "' (only letters/digits/_/- allowed)", source);
            return new Result(files, diagnostics);
        }
        MigratedSet set = EliteArmorReader.read(id, setYaml);
        warnUnmappedEffects(set.id(), set.effects(), diagnostics, source);
        files.put("sets/" + set.id() + ".yml", SchemaWriter.set(set));
        return new Result(files, diagnostics);
    }

    private static List<MigratedEffect> enchantLevelsEffects(MigratedEnchant enchant) {
        return enchant.levels().stream().map(MigratedLevel::effects).flatMap(List::stream).toList();
    }

    private static void warnUnmappedEffects(String id, List<MigratedEffect> effects,
                                            Diagnostics diagnostics, Source source) {
        for (MigratedEffect effect : effects) {
            if (!effect.mapped()) {
                diagnostics.warning("migrate.effect", "'" + id + "': effect '" + effect.legacy()
                        + "' was not translated — " + effect.note(), source);
            }
        }
    }
}
