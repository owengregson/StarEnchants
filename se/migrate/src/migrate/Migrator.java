package migrate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import migrate.model.MigratedEffect;
import migrate.model.MigratedEnchant;
import migrate.model.MigratedLevel;
import migrate.model.MigratedSet;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.spec.ParamSpec;

/**
 * The legacy-plugin importer entry point (docs/architecture.md §10). Reads a legacy plugin's configs,
 * maps them to the unified vocabulary, and produces StarEnchants content-format-v2 YAML keyed by output
 * path — plus a {@link Diagnostics} log of everything that needs manual attention (an unmapped
 * trigger/applies, an effect with no equivalent). The migration NEVER fails on an unmappable construct:
 * it migrates the structure and flags the gaps as warnings, so the operator gets a reviewable,
 * mostly-complete tree.
 *
 * <p>Readers: EliteEnchantments, EliteArmor, and AdvancedEnchantments. Each entry point has an overload
 * taking an effect-spec lookup ({@code specs}: head → {@link ParamSpec}); when supplied, effects are
 * written in the v2 <strong>verbose</strong> form so migrated configs are stored in the unified v2
 * format (ADR-0016). Without it, effects fall back to the terse string (still valid v2).
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

    /** Migrate EliteEnchantments {@code enchantments.yml} into {@code enchants/<id>.yml} (terse effects). */
    public static Result eliteEnchantments(String enchantmentsYaml) {
        return eliteEnchantments(enchantmentsYaml, null);
    }

    /** Migrate EliteEnchantments {@code enchantments.yml}; effects verbose when {@code specs} is supplied. */
    public static Result eliteEnchantments(String enchantmentsYaml, Function<String, ParamSpec> specs) {
        return migrateEnchants(EliteEnchantmentsReader.read(enchantmentsYaml), "EliteEnchantments",
                "EliteEnchantments/enchantments.yml#", specs);
    }

    /** Migrate an AdvancedEnchantments {@code enchantments.yml} into {@code enchants/<id>.yml} (terse). */
    public static Result advancedEnchantments(String enchantmentsYaml) {
        return advancedEnchantments(enchantmentsYaml, null);
    }

    /** Migrate AdvancedEnchantments {@code enchantments.yml}; effects verbose when {@code specs} is supplied. */
    public static Result advancedEnchantments(String enchantmentsYaml, Function<String, ParamSpec> specs) {
        return migrateEnchants(AdvancedEnchantmentsReader.read(enchantmentsYaml), "AdvancedEnchantments",
                "AdvancedEnchantments/enchantments.yml#", specs);
    }

    /** Migrate one EliteArmor set file into a {@code sets/<id>.yml} (terse effects). */
    public static Result eliteArmorSet(String id, String setYaml) {
        return eliteArmorSet(id, setYaml, null);
    }

    /** Migrate one EliteArmor set file; effects verbose when {@code specs} is supplied. */
    public static Result eliteArmorSet(String id, String setYaml, Function<String, ParamSpec> specs) {
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
        files.put("sets/" + set.id() + ".yml", SchemaWriter.set(set, specs));
        return new Result(files, diagnostics);
    }

    /** The shared enchant migration: id-safety + structural warnings + per-enchant YAML, for any reader. */
    private static Result migrateEnchants(List<MigratedEnchant> enchants, String origin, String sourcePrefix,
                                          Function<String, ParamSpec> specs) {
        Map<String, String> files = new LinkedHashMap<>();
        Diagnostics diagnostics = new Diagnostics();
        for (MigratedEnchant enchant : enchants) {
            Source source = Source.ofFile(sourcePrefix + enchant.id());
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
            files.put("enchants/" + enchant.id() + ".yml", SchemaWriter.enchant(enchant, origin, specs));
        }
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
