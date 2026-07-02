package bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.Compiler;
import compile.load.ItemsConfig;
import compile.load.ItemsLoader;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.load.MasterConfig;
import compile.load.MasterConfigLoader;
import compile.load.MenusConfig;
import compile.load.MenusLoader;
import compile.resolve.PlatformResolvers;
import engine.boot.ContentCompiler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import platform.resolve.Aliases;
import platform.resolve.HandleResolver;
import schema.diag.Diagnostic;
import schema.spec.HandleCategory;

/**
 * The shipped {@code cosmic-pack} config pack (ADR-0023) must compile clean through the real
 * registries, like {@link CatalogValidationTest} guards the default catalog — so a broken pack port can
 * never ship.
 *
 * <p>Unlike the default-catalog test, handle tokens here resolve <em>strictly</em>: each material/sound/
 * particle/entity/attribute token must exist in the floor ({@code 1.17.1}) Bukkit enums — through the
 * production {@link HandleResolver} + {@link Aliases}, exactly as the runtime resolves them. This is what
 * turns "the EE port loaded an EE-only token that no server has" (e.g. the {@code BLEED} particle, the
 * pre-flattening {@code ENDERDRAGON_GROWL} sound) from a silent runtime {@code E_UNKNOWN_HANDLE} on every
 * enchant into an offline build failure. Floor enums are the strictest universe (shipped content must run on
 * the floor too), and they are plain enums on 1.17.1 so resolution needs no server. Registry-backed handles
 * (potion effects, enchantments) stay permissive offline — their existence is owned by the live matrix.
 */
class CosmicPackValidationTest {

    private static final PlatformResolvers STRICT = new PlatformResolvers() {
        @Override public OptionalInt material(String t) { return strict(HandleCategory.MATERIAL, t, n -> enumExists(Material.class, n)); }
        @Override public OptionalInt sound(String t) { return strict(HandleCategory.SOUND, t, n -> enumExists(Sound.class, n)); }
        @Override public OptionalInt particle(String t) { return strict(HandleCategory.PARTICLE, t, n -> enumExists(Particle.class, n)); }
        @Override public OptionalInt entityType(String t) { return strict(HandleCategory.ENTITY_TYPE, t, n -> enumExists(EntityType.class, n)); }
        @Override public OptionalInt attribute(String t) { return strict(HandleCategory.ATTRIBUTE, t, n -> enumExists(Attribute.class, n)); }
        // Registry-backed handles can't be enumerated without a live server → permissive offline, live-owned.
        @Override public OptionalInt potionEffect(String t) { return OptionalInt.of(0); }
        @Override public OptionalInt enchantment(String t) { return OptionalInt.of(0); }
    };

    /** Resolve {@code token} the way the runtime does, but against the given floor-enum existence test. */
    private static OptionalInt strict(HandleCategory category, String token, Predicate<String> exists) {
        return HandleResolver.resolve(token, Aliases.forCategory(category), exists).isPresent()
                ? OptionalInt.of(0)
                : OptionalInt.empty();
    }

    private static <E extends Enum<E>> boolean enumExists(Class<E> type, String name) {
        try {
            Enum.valueOf(type, name);
            return true;
        } catch (IllegalArgumentException notAConstant) {
            return false;
        }
    }

    private static final Path PACK = Path.of("packs-src/cosmic-pack");

    @Test
    void cosmicPackContentCompilesClean() {
        Path content = PACK.resolve("content");
        assertTrue(Files.isDirectory(content), "Cosmic pack content not found from " + Path.of("").toAbsolutePath());

        Compiler compiler = ContentCompiler.production(STRICT);
        Library library = LibraryLoader.load(content, compiler, 0);

        String blocking = library.diagnostics().stream()
                .filter(Diagnostic::blocking)
                .map(Diagnostic::toString)
                .collect(Collectors.joining("\n  "));
        assertFalse(library.hasErrors(), () -> "Cosmic pack content has blocking diagnostics:\n  " + blocking);
        // 122 enchants × multiple levels — guard against a silent empty/partial load.
        assertTrue(library.snapshot().abilityCount() > 400,
                () -> "expected the full EE catalog, got " + library.snapshot().abilityCount() + " abilities");
    }

    @Test
    void cosmicPackItemsLoadClean() {
        Path items = PACK.resolve("items");
        assertTrue(Files.isDirectory(items), "Cosmic pack items not found");
        ItemsConfig config = ItemsLoader.load(items);
        String errors = config.diagnostics().stream()
                .filter(Diagnostic::blocking)
                .map(Diagnostic::toString)
                .collect(Collectors.joining("\n  "));
        assertFalse(config.hasErrors(), () -> "Cosmic pack items have blocking diagnostics:\n  " + errors);
        assertTrue(config.soulGem().isPresent(), "the Cosmic pack should carry a soul-gem likeness");
    }

    @Test
    void cosmicPackMenusLoadClean() {
        Path menus = PACK.resolve("menus");
        assertTrue(Files.isDirectory(menus), "Cosmic pack menus not found");
        MenusConfig config = MenusLoader.load(menus);
        String errors = config.diagnostics().stream()
                .filter(Diagnostic::blocking)
                .map(Diagnostic::toString)
                .collect(Collectors.joining("\n  "));
        assertFalse(config.hasErrors(), () -> "Cosmic pack menus have blocking diagnostics:\n  " + errors);
    }

    @Test
    void cosmicPackMasterConfigLoadsClean() {
        Path configFile = PACK.resolve("config.yml");
        assertTrue(Files.isRegularFile(configFile), "Cosmic pack config.yml not found");
        MasterConfig master = MasterConfigLoader.load(configFile);
        String errors = master.diagnostics().stream()
                .filter(Diagnostic::blocking)
                .map(Diagnostic::toString)
                .collect(Collectors.joining("\n  "));
        assertFalse(master.hasErrors(), () -> "Cosmic pack config.yml has blocking diagnostics:\n  " + errors);
    }

    // pack.yml is the ADR-0023 descriptor; the rest are the captured surface roots (pack.PackSurface FILES+DIRS).
    // cosmic-pack.zip is a BUILD output (se/bootstrap/build.gradle.kts packCosmicPack), never a source entry.
    private static final Set<String> ALLOWED_TOP_LEVEL = Set.of(
            "pack.yml", "config.yml", "lang.yml", "content", "items", "menus");

    @Test
    void cosmicPackHasOnlySurfaceRootsAtTopLevel() throws Exception {
        assertTrue(Files.isDirectory(PACK), "Cosmic pack source tree not found from " + Path.of("").toAbsolutePath());
        try (Stream<Path> top = Files.list(PACK)) {
            List<String> stray = top.map(p -> p.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .filter(name -> !ALLOWED_TOP_LEVEL.contains(name))
                    .sorted()
                    .toList();
            assertTrue(stray.isEmpty(),
                    () -> "cosmic-pack has top-level entries outside pack.yml + the surface roots: " + stray);
        }
    }
}
