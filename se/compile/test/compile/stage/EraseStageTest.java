package compile.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.model.Ability;
import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.model.CompiledSelector;
import compile.model.Interners;
import compile.model.SourceKind;
import compile.model.SourceMap;
import compile.model.StableKeyIndex;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.spec.Args;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DefaultEraseStage} — the source-erasure stage that interns names,
 * bit-packs the trigger mask and world bitset, assigns dense ids, and builds the
 * {@link StableKeyIndex} and {@link SourceMap} (docs/architecture.md §4.1, §5.3, §8).
 */
class EraseStageTest {

    private static final EraseStage STAGE = new DefaultEraseStage();

    private static CompiledEffect effect() {
        return new CompiledEffect("X", Args.empty(), CompiledSelector.SELF, 0, Affinity.CONTEXT_LOCAL);
    }

    /**
     * A lowered fixture with sensible defaults — only the fields a test cares about
     * are passed in; everything structural (level, chances, condition) is neutral.
     */
    private static LoweredAbility lowered(
            String stableKey,
            int defId,
            List<String> triggers,
            List<String> worldBlacklist,
            String suppressKey,
            String cdScopeEnchant,
            String cdScopeGroup,
            String cdScopeType,
            Source source) {
        return new LoweredAbility(
                SourceKind.ENCHANT,
                stableKey,
                defId,
                0,
                0.0,
                0,
                0,
                triggers,
                worldBlacklist,
                null,
                List.of(),
                suppressKey,
                cdScopeEnchant,
                cdScopeGroup,
                cdScopeType,
                0,
                Affinity.CONTEXT_LOCAL,
                source,
                0);
    }

    /** A minimal fixture: a stable key + defId, no names. */
    private static LoweredAbility lowered(String stableKey, int defId) {
        return lowered(stableKey, defId, List.of(), List.of(), null, null, null, null, Source.UNKNOWN);
    }

    @Test
    void assignsDenseIdsZeroToNMinusOne() {
        Diagnostics d = new Diagnostics();
        ErasedContent erased = STAGE.erase(
                List.of(lowered("a", 10), lowered("b", 11), lowered("c", 12)), d);

        assertFalse(d.hasErrors());
        assertEquals(3, erased.abilities().length);
        for (int i = 0; i < erased.abilities().length; i++) {
            assertEquals(i, erased.abilities()[i].id());
        }
    }

    @Test
    void sharedTriggerNameInternsToTheSameBit() {
        Diagnostics d = new Diagnostics();
        ErasedContent erased = STAGE.erase(List.of(
                lowered("a", 1, List.of("ATTACK"), List.of(), null, null, null, null, Source.UNKNOWN),
                lowered("b", 2, List.of("ATTACK", "MINE"), List.of(), null, null, null, null, Source.UNKNOWN)), d);

        assertFalse(d.hasErrors());
        Interners interners = erased.interners();
        int attackId = interners.triggers().idOf("ATTACK");
        int mineId = interners.triggers().idOf("MINE");

        Ability a = erased.abilities()[0];
        Ability b = erased.abilities()[1];
        assertTrue(a.firesOn(attackId));
        assertTrue(b.firesOn(attackId));
        assertTrue(b.firesOn(mineId));
        assertFalse(a.firesOn(mineId));
    }

    @Test
    void sharedWorldNameInternsToTheSameBit() {
        Diagnostics d = new Diagnostics();
        ErasedContent erased = STAGE.erase(List.of(
                lowered("a", 1, List.of(), List.of("world_nether"), null, null, null, null, Source.UNKNOWN),
                lowered("b", 2, List.of(), List.of("world_nether", "world_end"), null, null, null, null,
                        Source.UNKNOWN)), d);

        assertFalse(d.hasErrors());
        Interners interners = erased.interners();
        int netherId = interners.worlds().idOf("world_nether");
        int endId = interners.worlds().idOf("world_end");

        Ability a = erased.abilities()[0];
        Ability b = erased.abilities()[1];
        assertTrue(a.blockedInWorld(netherId));
        assertTrue(b.blockedInWorld(netherId));
        assertTrue(b.blockedInWorld(endId));
        assertFalse(a.blockedInWorld(endId));
    }

    @Test
    void suppressAndCooldownScopesInternConsistentlyAndNullMapsToMinusOne() {
        Diagnostics d = new Diagnostics();
        ErasedContent erased = STAGE.erase(List.of(
                lowered("a", 1, List.of(), List.of(), "DISABLE_FOO", "ENCH_A", "GROUP_A", "TYPE_A", Source.UNKNOWN),
                lowered("b", 2, List.of(), List.of(), "DISABLE_FOO", null, "GROUP_A", null, Source.UNKNOWN)), d);

        assertFalse(d.hasErrors());
        Ability a = erased.abilities()[0];
        Ability b = erased.abilities()[1];

        // Same suppress key across two abilities -> same id.
        assertEquals(a.suppressKey(), b.suppressKey());
        assertTrue(a.suppressKey() >= 0);

        // Same group scope -> same id; absent scopes -> -1.
        assertEquals(a.cdScopeGroup(), b.cdScopeGroup());
        assertTrue(a.cdScopeEnchant() >= 0);
        assertTrue(a.cdScopeType() >= 0);
        assertEquals(-1, b.cdScopeEnchant());
        assertEquals(-1, b.cdScopeType());

        Interners interners = erased.interners();
        assertEquals(a.suppressKey(), interners.suppress().idOf("DISABLE_FOO"));
        assertEquals(a.cdScopeGroup(), interners.cooldownScopes().idOf("GROUP_A"));
    }

    @Test
    void suppressEffectKeyInternsToTheSameScopeIdItsTargetAbilityLowersTo() {
        // SUPPRESS:GROUP:lifesteal — its key must intern to the SAME cooldownScopes id the
        // suppressed abilities lower their group scope to, so gate 5 matches them (the bridge invariant).
        Args suppressArgs = Args.empty()
                .with("scope", "GROUP").with("key", "lifesteal").with("duration", 200L);
        CompiledEffect suppress = new CompiledEffect(
                "SUPPRESS", suppressArgs, CompiledSelector.SELF, 0, Affinity.CONTEXT_LOCAL);
        LoweredAbility suppressor = new LoweredAbility(
                SourceKind.ENCHANT, "suppressor", 1, 0, 0.0, 0, 0,
                List.of(), List.of(), null, List.of(suppress),
                null, null, null, null, 0, Affinity.CONTEXT_LOCAL, Source.UNKNOWN, 0);
        LoweredAbility victim = lowered(
                "victim", 2, List.of(), List.of(), null, null, "lifesteal", null, Source.UNKNOWN);

        Diagnostics d = new Diagnostics();
        ErasedContent erased = STAGE.erase(List.of(suppressor, victim), d);
        assertFalse(d.hasErrors());

        Ability suppressorAbility = erased.abilities()[0];
        Ability victimAbility = erased.abilities()[1];
        CompiledEffect erasedSuppress = suppressorAbility.effects()[0];

        assertEquals(1L, erasedSuppress.args().lng("scope")); // GROUP → kind 1
        assertEquals(200L, erasedSuppress.args().lng("duration")); // untouched
        // the bridge: SUPPRESS key == the victim's group scope id == the cooldownScopes id of "lifesteal"
        assertEquals((long) victimAbility.cdScopeGroup(), erasedSuppress.args().lng("key"));
        assertEquals(erased.interners().cooldownScopes().idOf("lifesteal"), victimAbility.cdScopeGroup());
    }

    @Test
    void duplicateStableKeyIsReportedAndDropped() {
        Diagnostics d = new Diagnostics();
        ErasedContent erased = STAGE.erase(List.of(
                lowered("dup", 1),
                lowered("other", 2),
                lowered("dup", 3)), d);

        // Three lowered, one dropped -> two kept.
        assertEquals(2, erased.abilities().length);
        assertTrue(d.hasErrors());
        assertEquals("E_DUP_KEY", d.all().get(0).code());

        // Dense ids stay contiguous over the kept abilities.
        assertEquals(0, erased.abilities()[0].id());
        assertEquals(1, erased.abilities()[1].id());

        // The dropped duplicate's defId is not in the source map.
        assertNotNull(erased.sourceMap().lookup(1));
        assertNotNull(erased.sourceMap().lookup(2));
        assertNull(erased.sourceMap().lookup(3));
    }

    @Test
    void stableKeyIndexRoundTripsAndUnknownKeyIsMinusOne() {
        Diagnostics d = new Diagnostics();
        ErasedContent erased = STAGE.erase(
                List.of(lowered("alpha", 1), lowered("beta", 2)), d);

        StableKeyIndex index = erased.stableKeys();
        assertFalse(d.hasErrors());
        assertEquals(2, index.size());

        assertEquals(0, index.idOf("alpha"));
        assertEquals(1, index.idOf("beta"));
        assertEquals("alpha", index.keyOf(0));
        assertEquals("beta", index.keyOf(1));
        assertEquals(-1, index.idOf("does_not_exist"));
    }

    @Test
    void sourceMapLooksUpExpectedEntryByDefId() {
        Source src = Source.of("enchants.yml", 7, 3);
        Diagnostics d = new Diagnostics();
        ErasedContent erased = STAGE.erase(List.of(
                lowered("alpha", 42, List.of(), List.of(), null, null, null, null, src)), d);

        assertFalse(d.hasErrors());
        SourceMap.Entry entry = erased.sourceMap().lookup(42);
        assertNotNull(entry);
        assertEquals(SourceKind.ENCHANT, entry.sourceKind());
        assertEquals("alpha", entry.stableKey());
        assertSame(src, entry.source());
    }

    @Test
    void passesEffectsConditionAndScalarsThrough() {
        CompiledEffect e = effect();
        LoweredAbility la = new LoweredAbility(
                SourceKind.CRYSTAL,
                "withEffect",
                5,
                3,
                25.0,
                40,
                2,
                List.of(),
                List.of(),
                null,
                List.of(e),
                null,
                null,
                null,
                null,
                10,
                Affinity.CONTEXT_LOCAL,
                Source.UNKNOWN,
                0);

        Diagnostics d = new Diagnostics();
        ErasedContent erased = STAGE.erase(List.of(la), d);

        assertFalse(d.hasErrors());
        Ability a = erased.abilities()[0];
        assertEquals(SourceKind.CRYSTAL, a.sourceKind());
        assertEquals(5, a.defId());
        assertEquals(3, a.level());
        assertEquals(25.0, a.baseChance());
        assertEquals(40, a.cooldownTicks());
        assertEquals(2, a.soulCost());
        assertEquals(10, a.repeatTicks());
        assertNull(a.condition());
        assertEquals(1, a.effects().length);
        assertSame(e, a.effects()[0]);
        assertEquals(-1, a.suppressKey());
    }

    @Test
    void neverThrowsOnEmptyInput() {
        Diagnostics d = new Diagnostics();
        ErasedContent erased = STAGE.erase(List.of(), d);

        assertFalse(d.hasErrors());
        assertEquals(0, erased.abilities().length);
        assertEquals(0, erased.stableKeys().size());
        assertNotNull(erased.interners());
        assertNotNull(erased.sourceMap());
    }
}
