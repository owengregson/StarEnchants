package testfx;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.stream.Stream;

/**
 * Shared ArchUnit assertions for the load-bearing purity boundary (docs/architecture.md §2.1): a pure
 * language/compiler module must stay server-free so it is deterministically unit-testable. Owns the importer
 * option and the banned-package list once, so the four {@code CorePurityArchTest}s are one-line calls instead
 * of copy-pasted rules that drift.
 */
public final class Purity {

    // The server surfaces a pure module must never couple to. NMS (net.minecraft) and Paper internals are
    // as forbidden as the Bukkit API itself.
    private static final String[] SERVER_ROOTS = {"org.bukkit..", "net.minecraft..", "io.papermc.."};

    private Purity() {
    }

    /**
     * Fail if any PRODUCTION class under {@code rootPackage} depends on a server API. Tests are excluded, so a
     * module whose test classpath legitimately pulls in the engine (e.g. migrate) is not tripped by its own
     * fixtures. {@code extraBannedRoots} adds module-specific forbidden roots on top of the server ones.
     */
    public static void assertServerFree(String rootPackage, String... extraBannedRoots) {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(rootPackage);
        String[] banned = Stream.concat(Stream.of(SERVER_ROOTS), Stream.of(extraBannedRoots))
                .toArray(String[]::new);
        noClasses().should().dependOnClassesThat().resideInAnyPackage(banned).check(classes);
    }
}
