package tester.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Keeps the live suites from RE-DERIVING a production-rendered string — the formatting-coupling class that
 * bit {@code EconomyItemsSuite}'s transmog check (writing-tests Rule 1). A suite that rebuilds a display name
 * or lore line and asserts equality is the worst kind of brittle: production passes copy through a
 * configurable template AND Bukkit's {@code ItemMeta} (set→get normalises colour codes — a redundant
 * {@code §r§d} collapses to {@code §d}), so the rebuilt expectation drifts from the real value and the suite
 * fails on a formatting change that broke nothing. The canonical way in is re-implementing colour
 * translation, so this bans {@code org.bukkit.ChatColor} from the harness outright: colour translation lives
 * once, in {@code item.mint.ItemFactory.color}. A suite that must check rendered copy asserts STATE (PDC,
 * counts, booleans) or the STRUCTURE of the change, or compares against production's own output — never a
 * string it built itself.
 */
class RenderingDisciplineArchTest {

    @Test
    void harnessDoesNotReimplementColourTranslation() {
        JavaClasses harness = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("tester");
        ArchRule rule = noClasses().should().dependOnClassesThat()
                .haveFullyQualifiedName("org.bukkit.ChatColor")
                .because("a live suite must not reconstruct a production-rendered string — colour translation "
                        + "belongs to item.mint.ItemFactory.color; assert STATE or compare against production's "
                        + "own output, never a string the test rebuilds (writing-tests Rule 1)");
        rule.check(harness);
    }
}
