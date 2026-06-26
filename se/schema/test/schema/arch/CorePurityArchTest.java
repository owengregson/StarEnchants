package schema.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * CI lock for the load-bearing purity boundary (docs/architecture.md §2.1): {@code schema} is the DSL
 * as a pure language definition and must stay server-free so the compiler is deterministically
 * unit-testable. A stray {@code org.bukkit}/NMS/Paper import silently couples the language to a server —
 * this turns that into a build failure rather than a review catch.
 */
class CorePurityArchTest {

    @Test
    void dependsOnNoServerApi() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("schema");
        ArchRule rule = noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("org.bukkit..", "net.minecraft..", "io.papermc..");
        rule.check(classes);
    }
}
