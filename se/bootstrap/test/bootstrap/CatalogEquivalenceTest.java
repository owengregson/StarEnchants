package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.Compiler;
import compile.load.CrystalDef;
import compile.load.EnchantDef;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.load.SetDef;
import compile.model.Ability;
import compile.model.CompiledEffect;
import compile.resolve.PlatformResolvers;
import engine.boot.ContentCompiler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * A ONE-OFF equivalence gate for the v1→v2 catalog migration (ADR-0016): the v2 catalog must compile
 * to byte-identical ABILITIES as the v1 catalog — same stable keys, same per-ability chance/cooldown/
 * soul-cost/triggers/condition/effects (head + typed args + selector + WAIT) — proving the rewrite
 * changed only the authoring surface, never behaviour. Tiers/folders are metadata and (by design) do
 * not change the stable key, so the two snapshots must match exactly.
 *
 * <p>Gated on {@code -Dse.equiv.old=<v1 dir> -Dse.equiv.new=<v2 dir>} so it is a deliberate local
 * verification, never CI. One shared deterministically-interning resolver loads BOTH so a handle token
 * (potion/sound/material) interns to the same id in each — a CHANGED token surfaces as an arg mismatch
 * rather than being masked.
 */
class CatalogEquivalenceTest {

    /** Interns each distinct token to a stable per-category id; shared across both loads → consistent ids. */
    private static final class InterningResolvers implements PlatformResolvers {
        private final Map<String, Map<String, Integer>> byCategory = new LinkedHashMap<>();
        private final int[] next = {1};

        private OptionalInt intern(String category, String token) {
            int id = byCategory.computeIfAbsent(category, c -> new LinkedHashMap<>())
                    .computeIfAbsent(token, t -> next[0]++);
            return OptionalInt.of(id);
        }

        @Override public OptionalInt material(String token) { return intern("material", token); }
        @Override public OptionalInt sound(String token) { return intern("sound", token); }
        @Override public OptionalInt potionEffect(String token) { return intern("potion", token); }
        @Override public OptionalInt particle(String token) { return intern("particle", token); }
        @Override public OptionalInt enchantment(String token) { return intern("enchantment", token); }
        @Override public OptionalInt entityType(String token) { return intern("entity", token); }
        @Override public OptionalInt attribute(String token) { return intern("attribute", token); }
    }

    @Test
    void v2CatalogCompilesToTheSameAbilitiesAsV1() {
        String oldDir = System.getProperty("se.equiv.old");
        String newDir = System.getProperty("se.equiv.new");
        Assumptions.assumeTrue(oldDir != null && newDir != null,
                "set -Dse.equiv.old and -Dse.equiv.new to run the catalog equivalence gate");
        Assumptions.assumeTrue(Files.isDirectory(Path.of(oldDir)) && Files.isDirectory(Path.of(newDir)),
                "both equivalence dirs must exist");

        InterningResolvers shared = new InterningResolvers();
        Library v1 = LibraryLoader.load(Path.of(oldDir), ContentCompiler.production(shared), 0);
        Library v2 = LibraryLoader.load(Path.of(newDir), ContentCompiler.production(shared), 0);

        assertFalse(v1.hasErrors(), () -> "v1 catalog has errors: " + v1.diagnostics());
        assertFalse(v2.hasErrors(), () -> "v2 catalog has errors: " + v2.diagnostics());

        TreeSet<String> keysA = stableKeys(v1);
        TreeSet<String> keysB = stableKeys(v2);
        assertEquals(keysA, keysB, "the v2 catalog must define exactly the same stable keys as v1");

        // Collect EVERY mismatch in one pass so a single run lists all problem files to fix.
        List<String> diffs = new ArrayList<>();
        for (String key : keysA) {
            Ability a = v1.snapshot().byStableKey(key);
            Ability b = v2.snapshot().byStableKey(key);
            if (b == null) {
                diffs.add(key + ": missing in v2");
                continue;
            }
            diff(diffs, key, "level", a.level(), b.level());
            diff(diffs, key, "triggerMask", a.triggerMask(), b.triggerMask());
            diff(diffs, key, "chance", a.baseChance(), b.baseChance());
            diff(diffs, key, "cooldown", a.cooldownTicks(), b.cooldownTicks());
            diff(diffs, key, "soul-cost", a.soulCost(), b.soulCost());
            diff(diffs, key, "repeat", a.repeatTicks(), b.repeatTicks());
            diff(diffs, key, "pieces", a.setPieces(), b.setPieces());
            diff(diffs, key, "disabled-worlds", a.worldBlacklist(), b.worldBlacklist());
            diff(diffs, key, "condition", conditionSig(a), conditionSig(b));
            diff(diffs, key, "effects", effectSig(a), effectSig(b));
        }
        assertTrue(diffs.isEmpty(), () -> "v1↔v2 ability mismatches (" + diffs.size() + "):\n  "
                + String.join("\n  ", diffs));
    }

    private static void diff(List<String> out, String key, String field, Object a, Object b) {
        if (!java.util.Objects.equals(a, b)) {
            out.add(key + " " + field + ": v1=" + a + " v2=" + b);
        }
    }

    private static TreeSet<String> stableKeys(Library lib) {
        TreeSet<String> keys = new TreeSet<>();
        for (EnchantDef e : lib.catalog()) {
            for (int level = 1; level <= e.maxLevel(); level++) {
                keys.add(e.key() + "/" + level);
            }
        }
        for (CrystalDef c : lib.crystals()) {
            keys.add(c.key());
        }
        for (SetDef s : lib.sets()) {
            keys.add(s.key());
        }
        return keys;
    }

    /** The condition's LOGIC (its compiled tree), excluding its source position (which differs by file/line). */
    private static String conditionSig(Ability ability) {
        return ability.condition() == null ? "none" : String.valueOf(ability.condition().root());
    }

    /** A stable, order-preserving signature of an ability's effects: head, typed args, selector, WAIT. */
    private static List<String> effectSig(Ability ability) {
        List<String> sig = new ArrayList<>();
        for (CompiledEffect e : ability.effects()) {
            sig.add(e.head()
                    + " args=" + e.args().asMap()
                    + " @" + e.target().head() + e.target().args().asMap()
                    + " wait=" + e.cumulativeWaitTicks()
                    + " aff=" + e.affinity());
        }
        return sig;
    }
}
