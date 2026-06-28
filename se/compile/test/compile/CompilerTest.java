package compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.def.AbilityDef;
import compile.model.Ability;
import compile.model.Affinity;
import compile.model.Interners;
import compile.model.Snapshot;
import compile.model.SourceKind;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.EffectLine;
import schema.spec.D;
import schema.spec.ParamSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

/** End-to-end golden test for the whole compiler pipeline (docs/architecture.md §11). */
class CompilerTest {

    private static ParamSpec damage() {
        return ParamSpec.of("DAMAGE").param("amount", D.DOUBLE.min(0)).build();
    }

    private static ParamSpec heal() {
        return ParamSpec.of("HEAL").param("amount", D.DOUBLE.min(0)).build();
    }

    private static EffectLine line(String raw, String file, int row) {
        return EffectLine.parse(raw, Source.of(file, row, 1));
    }

    private static AbilityDef def(
            SourceKind kind, String key, int defId, int level, double chance, int cooldown,
            List<String> triggers, List<String> worlds, String condition, List<EffectLine> effects) {
        return new AbilityDef(kind, key, defId, level, chance, cooldown, 0,
                triggers, worlds, condition, effects, null, null, null, null, 0,
                Source.ofFile("content.yml"), 0);
    }

    private static List<AbilityDef> library() {
        return List.of(
                def(SourceKind.ENCHANT, "ench/lifesteal", 100, 2, 30.0, 40,
                        List.of("ATTACK"), List.of(), "%victim.health% < 6",
                        List.of(line("DAMAGE:5", "enchants.yml", 3),
                                line("WAIT:10", "enchants.yml", 4),
                                line("HEAL:3", "enchants.yml", 5))),
                def(SourceKind.SET, "set/yeti", 101, 0, 100.0, 0,
                        List.of("ATTACK", "DEFEND"), List.of("world_nether"), null,
                        List.of(line("DAMAGE:2", "armor.yml", 8))),
                def(SourceKind.CRYSTAL, "crystal/zap", 102, 0, 50.0, 0,
                        List.of("DEFEND"), List.of(), null,
                        List.of(line("HEAL:1", "crystals.yml", 2))),
                // duplicate stable key — must be reported and dropped
                def(SourceKind.ENCHANT, "ench/lifesteal", 103, 1, 10.0, 0,
                        List.of("ATTACK"), List.of(), null,
                        List.of(line("DAMAGE:9", "enchants.yml", 20))));
    }

    @Test
    void compilesLibraryIntoSnapshotWithHandComputedExpectations() {
        Diagnostics diags = new Diagnostics();
        Snapshot snap = Compiler.of(MapSpecRegistry.of(damage(), heal()))
                .compile(library(), 7, diags);

        assertEquals(7, snap.generation());
        assertTrue(snap.diagnostics().stream().anyMatch(d -> d.is(DiagCode.E_DUP_KEY)));

        // four defs in, one dropped → dense ids 0..2 in input order
        assertEquals(3, snap.abilityCount());
        for (int i = 0; i < snap.abilityCount(); i++) {
            assertEquals(i, snap.abilities()[i].id());
        }

        Ability lifesteal = snap.byStableKey("ench/lifesteal");
        Ability yeti = snap.byStableKey("set/yeti");
        Ability zap = snap.byStableKey("crystal/zap");
        assertNotNull(lifesteal);
        assertNotNull(yeti);
        assertNotNull(zap);
        assertNull(snap.byStableKey("nope"), "unknown key resolves to null, never crashes");
        assertEquals(0, lifesteal.id());
        assertEquals(1, yeti.id());
        assertEquals(2, zap.id());

        assertEquals(SourceKind.ENCHANT, lifesteal.sourceKind());
        assertEquals(2, lifesteal.level());
        assertEquals(30.0, lifesteal.baseChance());
        assertEquals(40, lifesteal.cooldownTicks());
        assertEquals(SourceKind.SET, yeti.sourceKind());

        Interners interners = snap.interners();
        int attack = interners.triggers().idOf("ATTACK");
        int defend = interners.triggers().idOf("DEFEND");
        assertTrue(lifesteal.firesOn(attack));
        assertTrue(yeti.firesOn(attack));
        assertTrue(yeti.firesOn(defend));
        assertTrue(zap.firesOn(defend));

        // only yeti declared a world blacklist
        int nether = interners.worlds().idOf("world_nether");
        assertTrue(yeti.blockedInWorld(nether));
        assertEquals(0L, lifesteal.worldBlacklist());

        assertNotNull(lifesteal.condition(), "condition expression lowered to an AST");
        assertNull(yeti.condition());
        assertEquals(2, lifesteal.effects().length); // DAMAGE, HEAL (WAIT is timing, not an effect)
        assertEquals("DAMAGE", lifesteal.effects()[0].head());
        assertEquals(0, lifesteal.effects()[0].cumulativeWaitTicks());
        assertEquals("HEAL", lifesteal.effects()[1].head());
        assertEquals(10, lifesteal.effects()[1].cumulativeWaitTicks());
        assertEquals(5.0, lifesteal.effects()[0].args().dbl("amount"));

        assertEquals(Affinity.CONTEXT_LOCAL, lifesteal.affinity());

        assertEquals(SourceKind.ENCHANT, snap.sourceMap().lookup(100).sourceKind());
        assertEquals("ench/lifesteal", snap.sourceMap().lookup(100).stableKey());
        assertEquals(SourceKind.CRYSTAL, snap.sourceMap().lookup(102).sourceKind());
    }
}
