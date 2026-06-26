package pack.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * CI lock for the load-bearing purity boundary (docs/architecture.md §2.1): {@code pack} is the config-
 * pack ZIP snapshot surface and must stay server-free (it operates on config bytes, not a live server).
 * A stray {@code org.bukkit}/NMS/Paper import is a build failure.
 */
class CorePurityArchTest {

    @Test
    void dependsOnNoServerApi() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("pack");
        ArchRule rule = noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("org.bukkit..", "net.minecraft..", "io.papermc..");
        rule.check(classes);
    }
}
