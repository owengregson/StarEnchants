package api.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * CI lock for the reason {@code :api} exists (ADR-0038; docs/architecture.md §2, §7): it is the curated
 * PUBLIC surface, so it must depend on nothing internal. An add-on compiling against {@code :api} sees
 * exactly {@code api.*}, the schema DSL types, the JDK, and Bukkit — never an engine/compile/item internal
 * that would drift or leak the implementation. A stray {@code import engine.*} (e.g. re-adding the old
 * {@code api(":engine")} re-export) becomes a build failure here rather than a review catch.
 */
class ApiBoundaryArchTest {

    @Test
    void dependsOnlyOnSchemaJavaAndBukkit() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                // DO_NOT_INCLUDE_TESTS recognises a test class by the conventional `build/classes/java/test`
                // path, which the legacy tree (`build-legacy/`, §11) does not match — so on that lane THIS class
                // was imported and the rule failed on its own ArchUnit imports. Match on the tail alone.
                .withImportOption(location -> !location.contains("/classes/java/test/"))
                .importPackages("api");
        ArchRule rule = classes().should().onlyDependOnClassesThat()
                .resideInAnyPackage("api..", "schema..", "java..", "javax..", "org.bukkit..");
        rule.check(classes);
    }
}
