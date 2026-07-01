package item.worn;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import compile.model.Ability;
import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.model.SourceKind;
import compile.model.StableKeyIndex;
import item.codec.CombatState;
import item.codec.HeroicStat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the worn-state resolution core (ADR-0014, §5.5): on-item enchant {@code (baseKey,
 * level)} composes the path-derived per-level stable key, crystals resolve directly, unknown keys are
 * skipped, and multiplicity is preserved across pieces. Exercises the package-private {@code resolveFrom}
 * with hand-built keys/abilities — no server. The full equipment-reading path is verified live.
 */
class WornResolverTest {

    private static final IntPredicate ATTACK = t -> t == 0;
    private static final IntPredicate DEFENSE = t -> t == 1;
    private static final int TRIGGERS = 2;

    // id 0 = enchants/lifesteal/3, id 1 = crystals/zap — both fire on attack trigger 0.
    private static final StableKeyIndex KEYS =
            new StableKeyIndex(List.of("enchants/lifesteal/3", "crystals/zap"));
    private static final Ability[] ABILITIES = {ability(0, 1 << 0), ability(1, 1 << 0)};

    private static Ability ability(int id, int triggerMask) {
        return new Ability(id, 0, SourceKind.ENCHANT, triggerMask, 1, 100.0, 0, 0, 0L, null,
                new CompiledEffect[0], 0, Affinity.CONTEXT_LOCAL, -1, -1, -1, -1, 0);
    }

    private static WornResolver resolver() {
        return new WornResolver(null, TRIGGERS, ATTACK, DEFENSE); // itemViews unused by resolveFrom
    }

    private static WornResolver resolver(WornResolver.Features features) {
        return new WornResolver(null, TRIGGERS, ATTACK, DEFENSE, () -> features);
    }

    private static WornResolver resolver(java.util.Set<String> nonStackable) {
        return new WornResolver(null, TRIGGERS, ATTACK, DEFENSE, () -> WornResolver.Features.ALL, () -> nonStackable);
    }

    @Test
    void nonStackableCrystalContributesOncePerWearer() {
        // §ADR-0035: a NON-stackable crystal on two worn pieces contributes its ability ONCE; a stackable crystal
        // keeps full multiplicity. Dedup is by base key and scoped to crystals — enchants are unaffected.
        StableKeyIndex keys = new StableKeyIndex(List.of("crystals/dark", "crystals/water"));
        Ability[] abilities = {ability(0, 1 << 0), ability(1, 1 << 0)}; // both fire on attack trigger 0
        CombatState dark = new CombatState(Map.of(), List.of("crystals/dark"));
        CombatState water = new CombatState(Map.of(), List.of("crystals/water"));

        // dark is non-stackable: two pieces → id 0 tracked ONCE.
        WornState twoDark = resolver(java.util.Set.of("crystals/dark"))
                .resolveFrom(List.of(dark, dark), keys, abilities, 1);
        assertArrayEquals(new int[] {0}, sorted(twoDark.byTrigger(0)), "non-stackable dark fires once, not twice");
        assertArrayEquals(new int[] {0}, sorted(twoDark.activeCrystalAbilityIds()), "and is tracked once");

        // water is NOT in the non-stackable set: two pieces → id 1 twice (multiplicity preserved).
        WornState twoWater = resolver(java.util.Set.of("crystals/dark"))
                .resolveFrom(List.of(water, water), keys, abilities, 1);
        assertEquals(2, twoWater.byTrigger(0).length, "stackable water keeps multiplicity across pieces");
    }

    @Test
    void composesEnchantKeysAndResolvesCrystals() {
        CombatState combat = new CombatState(Map.of("enchants/lifesteal", 3), List.of("crystals/zap"));
        WornState worn = resolver().resolveFrom(List.of(combat), KEYS, ABILITIES, 7);

        assertEquals(7, worn.gen());
        assertArrayEquals(new int[] {0, 1}, sorted(worn.byTrigger(0)));      // both fire on trigger 0
        assertArrayEquals(new int[] {0, 1}, sorted(worn.combatAttack()));    // trigger 0 is attack-direction
        assertArrayEquals(new int[] {1}, worn.activeCrystalAbilityIds());    // zap tracked as a crystal
    }

    @Test
    void multiCrystalEntryResolvesBothComponents() {
        // A multi-crystal occupies ONE crystal-list entry "a+b" but contributes BOTH ability ids (§E);
        // the additive fold then sums overlapping effect magnitudes downstream.
        StableKeyIndex keys = new StableKeyIndex(List.of("crystals/zap", "crystals/frost"));
        Ability[] abilities = {ability(0, 1 << 0), ability(1, 1 << 0)};
        CombatState combat = new CombatState(Map.of(), List.of("crystals/zap+crystals/frost"));
        WornState worn = resolver().resolveFrom(List.of(combat), keys, abilities, 1);
        assertArrayEquals(new int[] {0, 1}, sorted(worn.activeCrystalAbilityIds()), "both components tracked");
        assertArrayEquals(new int[] {0, 1}, sorted(worn.byTrigger(0)), "both components fire");
    }

    @Test
    void unknownKeysAreSkippedNotCrashed() {
        CombatState combat = new CombatState(Map.of("enchants/ghost", 9), List.of("crystals/missing"));
        WornState worn = resolver().resolveFrom(List.of(combat), KEYS, ABILITIES, 1);
        assertEquals(0, worn.byTrigger(0).length);
        assertEquals(0, worn.activeCrystalAbilityIds().length);
    }

    @Test
    void multiplicityIsPreservedAcrossPieces() {
        CombatState a = new CombatState(Map.of("enchants/lifesteal", 3), List.of());
        CombatState b = new CombatState(Map.of("enchants/lifesteal", 3), List.of());
        WornState worn = resolver().resolveFrom(List.of(a, b), KEYS, ABILITIES, 1);
        assertEquals(2, worn.byTrigger(0).length); // lifesteal on two pieces → id 0 twice
    }

    // A SET bonus: id 0, fires on DEFENSE (trigger 1), completes at 3 worn pieces (setPieces=3).
    private static final StableKeyIndex SET_KEYS = new StableKeyIndex(List.of("sets/yeti"));
    private static final Ability[] SET_ABILITIES = {
        new Ability(0, 0, SourceKind.SET, 1 << 1, 0, 100.0, 0, 0, 0L, null,
                new CompiledEffect[0], 0, Affinity.CONTEXT_LOCAL, -1, -1, -1, -1, 3)
    };

    @Test
    void setBonusActivatesAtThresholdAndStaysOffBelowIt() {
        CombatState piece = new CombatState(Map.of(), List.of(), "sets/yeti", false);

        WornState below = resolver().resolveFrom(List.of(piece, piece), SET_KEYS, SET_ABILITIES, 1);
        assertEquals(0, below.byTrigger(1).length, "2 of 3 pieces — bonus must not fire");
        assertEquals(false, below.isSetActive(0));

        WornState met = resolver().resolveFrom(List.of(piece, piece, piece), SET_KEYS, SET_ABILITIES, 1);
        assertArrayEquals(new int[] {0}, met.byTrigger(1), "3 pieces — bonus fires on DEFENSE");
        assertEquals(true, met.isSetActive(0));
        assertEquals(0, met.byTrigger(0).length, "bonus does not fire on ATTACK");
    }

    @Test
    void omniPieceCompletesAPartiallyWornSet() {
        CombatState piece = new CombatState(Map.of(), List.of(), "sets/yeti", false);
        CombatState omni = new CombatState(Map.of(), List.of(), null, true);

        // 2 real + 1 omni = 3 → the set completes (§6.6).
        WornState worn = resolver().resolveFrom(List.of(piece, piece, omni), SET_KEYS, SET_ABILITIES, 1);
        assertEquals(true, worn.isSetActive(0));
        assertArrayEquals(new int[] {0}, worn.byTrigger(1));
    }

    @Test
    void heroicStatsSumAcrossPieces() {
        CombatState a = new CombatState(Map.of(), List.of(), null, false, new HeroicStat(0.20, 0.10, 0.0));
        CombatState b = new CombatState(Map.of(), List.of(), null, false, new HeroicStat(0.30, 0.40, 0.50));
        WornState worn = resolver().resolveFrom(List.of(a, b), KEYS, ABILITIES, 1);
        assertEquals(0.50, worn.heroic().percentDamage(), 1e-9);
        assertEquals(0.50, worn.heroic().percentReduction(), 1e-9);
        assertEquals(0.50, worn.heroic().durability(), 1e-9);
    }

    @Test
    void extraArmorBonusesFireWhileTheSetIsComplete() {
        // A set with a primary on:armor bonus (setId, completes at 3) PLUS an extra on:armor bonus (/a1,
        // setPieces 0). Both fire while complete; neither below threshold — the new multi-bonus path (§6.6).
        StableKeyIndex keys = new StableKeyIndex(List.of("sets/yeti", "sets/yeti/a1"));
        Ability[] abilities = {
            new Ability(0, 0, SourceKind.SET, 1 << 1, 0, 100.0, 0, 0, 0L, null, // primary: DEFENSE, completes at 3
                    new CompiledEffect[0], 0, Affinity.CONTEXT_LOCAL, -1, -1, -1, -1, 3),
            new Ability(1, 0, SourceKind.SET, 1 << 0, 0, 100.0, 0, 0, 0L, null, // extra: ATTACK, setPieces 0
                    new CompiledEffect[0], 0, Affinity.CONTEXT_LOCAL, -1, -1, -1, -1, 0)
        };
        CombatState piece = new CombatState(Map.of(), List.of(), "sets/yeti", false);

        WornState below = resolver().resolveFrom(List.of(piece, piece), keys, abilities, 1);
        assertEquals(0, below.byTrigger(0).length, "extra bonus must not fire below threshold");
        assertEquals(0, below.byTrigger(1).length);

        WornState complete = resolver().resolveFrom(List.of(piece, piece, piece), keys, abilities, 1);
        assertArrayEquals(new int[] {0}, complete.byTrigger(1), "primary fires on DEFENSE");
        assertArrayEquals(new int[] {1}, complete.byTrigger(0), "extra armour bonus fires on ATTACK when complete");
    }

    @Test
    void weaponBonusFiresOnlyWhenTheSetIsCompleteAndWeaponHeld() {
        // id 0 = sets/yeti armour bonus (DEFENSE, completes at 3); id 1 = sets/yeti/w1 (ATTACK, gated).
        StableKeyIndex keys = new StableKeyIndex(List.of("sets/yeti", "sets/yeti/w1"));
        Ability[] abilities = {
            new Ability(0, 0, SourceKind.SET, 1 << 1, 0, 100.0, 0, 0, 0L, null,
                    new CompiledEffect[0], 0, Affinity.CONTEXT_LOCAL, -1, -1, -1, -1, 3),
            new Ability(1, 0, SourceKind.SET, 1 << 0, 0, 100.0, 0, 0, 0L, null,
                    new CompiledEffect[0], 0, Affinity.CONTEXT_LOCAL, -1, -1, -1, -1, 0)
        };
        CombatState armor = new CombatState(Map.of(), List.of(), "sets/yeti", false);
        CombatState weapon = CombatState.weaponMember("sets/yeti");

        // 2 armour + weapon held: the weapon does NOT count toward completion, so the set is incomplete
        // and the weapon bonus must not fire.
        WornState incomplete = resolver().resolveFrom(List.of(armor, armor, weapon), keys, abilities, 1);
        assertEquals(false, incomplete.isSetActive(0), "weapon must not count toward completion");
        assertEquals(0, incomplete.byTrigger(0).length, "weapon bonus must not fire while the set is incomplete");

        // 3 armour (complete) + weapon held: armour bonus on DEFENSE, weapon bonus on ATTACK.
        WornState complete = resolver().resolveFrom(List.of(armor, armor, armor, weapon), keys, abilities, 1);
        assertArrayEquals(new int[] {0}, complete.byTrigger(1), "armour bonus fires on DEFENSE");
        assertArrayEquals(new int[] {1}, complete.byTrigger(0), "weapon bonus fires on ATTACK when complete + held");

        // 3 armour (complete) but weapon NOT held: only the armour bonus.
        WornState noWeapon = resolver().resolveFrom(List.of(armor, armor, armor), keys, abilities, 1);
        assertEquals(0, noWeapon.byTrigger(0).length, "no weapon held → no weapon bonus");
    }

    @Test
    void disabledFeaturesDropTheirSources() {
        // §L config.yml features.* — a disabled feature's source is skipped at resolve time.
        CombatState combat = new CombatState(Map.of("enchants/lifesteal", 3), List.of("crystals/zap"));

        // enchants off: only the crystal survives (id 1).
        WornState noEnchants = resolver(new WornResolver.Features(false, true, true, true))
                .resolveFrom(List.of(combat), KEYS, ABILITIES, 1);
        assertArrayEquals(new int[] {1}, sorted(noEnchants.byTrigger(0)), "enchant dropped, crystal kept");

        // crystals off: only the enchant survives (id 0) and no crystal is tracked.
        WornState noCrystals = resolver(new WornResolver.Features(true, true, false, true))
                .resolveFrom(List.of(combat), KEYS, ABILITIES, 1);
        assertArrayEquals(new int[] {0}, sorted(noCrystals.byTrigger(0)), "crystal dropped, enchant kept");
        assertEquals(0, noCrystals.activeCrystalAbilityIds().length);

        // sets off: a worn set never completes.
        CombatState piece = new CombatState(Map.of(), List.of(), "sets/yeti", false);
        WornState noSets = resolver(new WornResolver.Features(true, false, true, true))
                .resolveFrom(List.of(piece, piece, piece), SET_KEYS, SET_ABILITIES, 1);
        assertEquals(false, noSets.isSetActive(0), "sets off → no completion");
        assertEquals(0, noSets.byTrigger(1).length);

        // heroic off: worn heroic stats do not accumulate.
        CombatState heroicPiece =
                new CombatState(Map.of(), List.of(), null, false, new HeroicStat(0.5, 0.5, 0.5));
        WornState noHeroic = resolver(new WornResolver.Features(true, true, true, false))
                .resolveFrom(List.of(heroicPiece), KEYS, ABILITIES, 1);
        assertEquals(0.0, noHeroic.heroic().percentDamage(), 1e-9, "heroic off → no flat stat");
    }

    @Test
    void omniAloneCannotConjureASet() {
        // Omni pieces with NO real piece of the set must not complete it (§6.6).
        CombatState omni = new CombatState(Map.of(), List.of(), null, true);
        WornState worn = resolver().resolveFrom(List.of(omni, omni, omni), SET_KEYS, SET_ABILITIES, 1);
        assertEquals(false, worn.isSetActive(0));
        assertEquals(0, worn.byTrigger(1).length);
    }

    private static int[] sorted(int[] values) {
        int[] copy = values.clone();
        Arrays.sort(copy);
        return copy;
    }
}
