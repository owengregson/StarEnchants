package migrate.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * CI lock for the load-bearing purity boundary (docs/architecture.md §2.1): {@code migrate} is the
 * legacy-NBT reader + EE/EA/AE config importer and must stay server-free (it transforms config text into
 * the pure schema, never touching a live server). A stray {@code org.bukkit}/NMS/Paper import in
 * production code is a build failure. (Only production classes are scanned — the engine dependency on
 * the test classpath is intentionally excluded.)
 */
class CorePurityArchTest {

    @Test
    void dependsOnNoServerApi() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("migrate");
        ArchRule rule = noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("org.bukkit..", "net.minecraft..", "io.papermc..");
        rule.check(classes);
    }
}
