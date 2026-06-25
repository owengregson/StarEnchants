package migrate;

import compile.Compiler;
import compile.SpecRegistry;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.resolve.PlatformResolvers;
import engine.boot.ContentCompiler;
import engine.effect.kind.BuiltinEffects;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import schema.diag.Diagnostic;
import schema.spec.ParamSpec;

/**
 * One-off generator (NOT a CI test) that bootstraps the EliteEnchantments → StarEnchants enchant port for
 * the {@code elite-enchantments} config pack (ADR-0023). Disabled unless {@code -Dse.eeport.run=true}.
 *
 * <pre>
 *   ./gradlew :migrate:test --tests "*EePortGenerator" -Dse.eeport.run=true \
 *       -Dse.eeport.src=/Users/owengregson/Documents/EliteEnchantments \
 *       -Dse.eeport.out=/tmp/ee-port
 * </pre>
 *
 * <p>It runs the production EE migrator, rewrites each legacy {@code group:} into a {@code tier:}, compiles
 * the result, and prints a coverage report (unmapped effect heads with counts) for the hand-finishing pass.
 */
class EePortGenerator {

    private static final PlatformResolvers PERMISSIVE = new PlatformResolvers() {
        @Override public OptionalInt material(String t) { return OptionalInt.of(0); }
        @Override public OptionalInt sound(String t) { return OptionalInt.of(0); }
        @Override public OptionalInt potionEffect(String t) { return OptionalInt.of(0); }
        @Override public OptionalInt particle(String t) { return OptionalInt.of(0); }
        @Override public OptionalInt enchantment(String t) { return OptionalInt.of(0); }
        @Override public OptionalInt entityType(String t) { return OptionalInt.of(0); }
        @Override public OptionalInt attribute(String t) { return OptionalInt.of(0); }
    };

    private static final Function<String, ParamSpec> SPECS;
    static {
        SpecRegistry reg = BuiltinEffects.registry().specRegistry();
        SPECS = head -> reg.lookup(head).orElse(null);
    }

    private static final Pattern GROUP_LINE = Pattern.compile("(?m)^group: \"(.*)\"$");

    @Test
    @EnabledIfSystemProperty(named = "se.eeport.run", matches = "true")
    void generate() throws Exception {
        Path src = Path.of(System.getProperty("se.eeport.src", "/Users/owengregson/Documents/EliteEnchantments"));
        Path out = Path.of(System.getProperty("se.eeport.out", "/tmp/ee-port"));
        String enchantsYaml = Files.readString(src.resolve("enchantments.yml"), StandardCharsets.UTF_8);

        Migrator.Result result = Migrator.eliteEnchantments(enchantsYaml, SPECS);

        // The EE rarity is BOTH the SE tier (lore colour/sort) AND the SE group (SUPPRESS:GROUP matches), so
        // add a tier alongside the migrated group.
        Map<String, String> rewritten = new LinkedHashMap<>();
        TreeMap<String, Integer> tiersSeen = new TreeMap<>();
        for (Map.Entry<String, String> file : result.files().entrySet()) {
            Matcher m = GROUP_LINE.matcher(file.getValue());
            String body = file.getValue();
            if (m.find()) {
                String tier = m.group(1).toLowerCase(java.util.Locale.ROOT);
                tiersSeen.merge(tier, 1, Integer::sum);
                body = m.replaceFirst(Matcher.quoteReplacement(m.group() + "\ntier: " + tier));
            }
            rewritten.put(file.getKey(), body);
        }

        // Write the enchant tree + a tiers.yml covering every tier seen, then compile it.
        Files.createDirectories(out);
        for (Map.Entry<String, String> file : rewritten.entrySet()) {
            Path target = out.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
        }
        Files.writeString(out.resolve("tiers.yml"), tiersYaml(), StandardCharsets.UTF_8);

        Compiler compiler = ContentCompiler.production(PERMISSIVE);
        Library library = LibraryLoader.load(out, compiler, 0);

        // Coverage report.
        long blocking = library.diagnostics().stream().filter(Diagnostic::blocking).count();
        TreeMap<String, Integer> unmapped = new TreeMap<>();
        for (Diagnostic d : result.diagnostics().all()) {
            if (d.code().equals("migrate.effect")) {
                String head = effectHead(d.message());
                unmapped.merge(head, 1, Integer::sum);
            }
        }
        System.out.println("\n===== EE → SE PORT REPORT =====");
        System.out.println("enchants migrated:   " + result.files().size());
        System.out.println("tiers seen:          " + tiersSeen);
        System.out.println("compiled abilities:  " + library.snapshot().abilityCount());
        System.out.println("BLOCKING errors:     " + blocking);
        System.out.println("unmapped effects (" + unmapped.values().stream().mapToInt(i -> i).sum()
                + " occurrences, " + unmapped.size() + " distinct heads):");
        unmapped.forEach((head, n) -> System.out.println("    " + n + "x  " + head));
        if (blocking > 0) {
            System.out.println("\n--- blocking diagnostics ---");
            library.diagnostics().stream().filter(Diagnostic::blocking).limit(40)
                    .forEach(d -> System.out.println("    " + d));
        }
        System.out.println("output: " + out.toAbsolutePath());
        System.out.println("================================\n");
    }

    /** Extract the legacy effect token head from a {@code migrate.effect} diagnostic message. */
    private static String effectHead(String message) {
        // message form: 'id': effect 'TOKEN:args' was not translated — note
        int open = message.indexOf("effect '");
        if (open < 0) {
            return message;
        }
        int start = open + "effect '".length();
        int end = message.indexOf('\'', start);
        String token = end < 0 ? message.substring(start) : message.substring(start, end);
        int colon = token.indexOf(':');
        return colon < 0 ? token : token.substring(0, colon);
    }

    private static String tiersYaml() {
        return """
                default-tier: common
                tiers:
                  common:    { color: "&7",   weight: 10, glint: false }
                  uncommon:  { color: "&a",   weight: 20, glint: false }
                  rare:      { color: "&b",   weight: 30, glint: false }
                  epic:      { color: "&e",   weight: 40, glint: true  }
                  legendary: { color: "&6",   weight: 50, glint: true  }
                  soul:      { color: "&c",   weight: 60, glint: true  }
                  heroic:    { color: "&d",   weight: 70, glint: true  }
                  mythic:    { color: "&4",   weight: 80, glint: true  }
                """;
    }
}
