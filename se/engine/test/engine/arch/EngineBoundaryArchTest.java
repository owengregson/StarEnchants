package engine.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * CI lock for the engine's two structural budgets (docs/architecture.md §3.6, §8; performance-hot-paths):
 * the scheduler boundary (only the Sink knows about threads) and the allocation-light combat hot path.
 * These turn the documented rules into build failures rather than review catches.
 */
class EngineBoundaryArchTest {

    // The packages the combat/item hot path runs in (§8): a gated hit walks pipeline → run over stores,
    // conditions, and interact arbiters. Cold paths (boot, doc, spec, effect kinds) are unconstrained.
    private static final String[] HOT_PATH = {
        "engine.pipeline..", "engine.run..", "engine.condition..", "engine.interact..", "engine.stores..",
    };

    private static JavaClasses engine;

    @BeforeAll
    static void importEngine() {
        engine = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("engine");
    }

    /**
     * Only the Sink dispatcher (§3.6 "the only code that knows about threads") and the boot compiler may
     * touch the scheduling abstraction. Everything else emits intents; it never schedules. Today only
     * engine.sink depends on platform.sched — engine.boot is allowed headroom for the compile driver.
     */
    @Test
    void onlySinkAndBootTouchScheduling() {
        noClasses().that().resideOutsideOfPackages("engine.sink..", "engine.boot..")
                .should().dependOnClassesThat().resideInAnyPackage("platform.sched..")
                .check(engine);
    }

    /** No engine class reaches for the raw Bukkit scheduler — entity/world work routes through platform.sched (§3.6). */
    @Test
    void noEngineClassCallsTheBukkitScheduler() {
        noClasses().should().callMethod(Bukkit.class, "getScheduler").check(engine);
    }

    /** No String#split / Pattern#compile / ItemStack#clone on the hot path — all parsing/cloning happens at load (§8). */
    @Test
    void hotPathAllocatesNoStringSplitRegexOrItemClone() {
        noClasses().that().resideInAnyPackage(HOT_PATH)
                .should().callMethod(String.class, "split", String.class)
                .orShould().callMethod(String.class, "split", String.class, int.class)
                .orShould().callMethod(Pattern.class, "compile", String.class)
                .orShould().callMethod(Pattern.class, "compile", String.class, int.class)
                .orShould().callMethod(ItemStack.class, "clone")
                .check(engine);
    }

    /** No YAML/JSON parsing on the hot path — content is compiled into the immutable Snapshot at load (§8). */
    @Test
    void hotPathDependsOnNoYamlOrJson() {
        noClasses().that().resideInAnyPackage(HOT_PATH)
                .should().dependOnClassesThat().resideInAnyPackage("com.google.gson..", "org.yaml.snakeyaml..")
                .check(engine);
    }
}
