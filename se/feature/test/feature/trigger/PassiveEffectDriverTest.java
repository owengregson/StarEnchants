package feature.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import compile.model.Ability;
import compile.model.CompiledEffect;
import compile.model.CompiledSelector;
import compile.model.Snapshot;
import engine.stores.CooldownStore;
import engine.stores.SuppressionStore;
import item.codec.HeroicStat;
import item.worn.WornState;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import schema.spec.Args;

/**
 * Unit-pins {@link PassiveEffectDriver#computeDesired} — the pure re-derivation of which self-buff potions a
 * player should currently have from their worn PASSIVE/HELD abilities, and which a DISABLE'd ability would
 * otherwise grant. The actual apply/remove is covered live; this nails the suppression filter + strongest-wins
 * merge + force-clear set that make a DISABLE_ENCHANT restore the CORRECT enchants afterwards.
 */
class PassiveEffectDriverTest {

    private static final int HELD = 11;
    private static final int PASSIVE = 8;
    private static final int GEN = 1;
    private static final int WIDTH = 12;

    // Two distinct interned potion-effect handle ids used across the cases.
    private static final int SPEED = 100;
    private static final int HASTE = 101;

    private static final int SCOPE_ENCHANT = 0; // mirrors ActivationPipeline / CooldownStore packing

    @Test
    void wornNonSuppressedPotionsAreDesired() {
        Ability speed = potionAbility(SPEED, 2, 5);
        Snapshot snapshot = snapshot(speed);
        SuppressionStore suppression = new SuppressionStore();

        PassiveEffectDriver.Desired d = PassiveEffectDriver.computeDesired(
                worn(passive(0)), snapshot, suppression, UUID.randomUUID(), 0L, HELD, PASSIVE);

        assertEquals(Map.of(SPEED, 1), d.apply()); // level 2 → amplifier 1
        assertTrue(d.suppressed().isEmpty());
    }

    @Test
    void strongestAmplifierWinsWhenTwoSourcesGrantTheSameType() {
        Ability weak = potionAbility(SPEED, 1, 5);   // amp 0
        Ability strong = potionAbility(SPEED, 3, 6);  // amp 2
        Snapshot snapshot = snapshot(weak, strong);
        PassiveEffectDriver.Desired d = PassiveEffectDriver.computeDesired(
                worn(passive(0, 1)), snapshot, new SuppressionStore(), UUID.randomUUID(), 0L, HELD, PASSIVE);

        assertEquals(Map.of(SPEED, 2), d.apply()); // the stronger amplifier (2) wins
    }

    @Test
    void aSuppressedAbilityContributesNothingButIsForceCleared() {
        Ability speed = potionAbility(SPEED, 2, 7); // enchant scope id 7
        Snapshot snapshot = snapshot(speed);
        SuppressionStore suppression = new SuppressionStore();
        UUID player = UUID.randomUUID();
        // DISABLE the enchant scope at tick 0 for 200 ticks.
        suppression.suppress(player, CooldownStore.key(SCOPE_ENCHANT, 7), 0L, 200);

        PassiveEffectDriver.Desired during = PassiveEffectDriver.computeDesired(
                worn(passive(0)), snapshot, suppression, player, 50L, HELD, PASSIVE);
        assertTrue(during.apply().isEmpty(), "a disabled passive grants no effect");
        assertEquals(Set.of(SPEED), during.suppressed(), "and its type is force-cleared mid-window");

        // After the window, the same worn state re-derives the effect back — the CORRECT set is restored.
        PassiveEffectDriver.Desired after = PassiveEffectDriver.computeDesired(
                worn(passive(0)), snapshot, suppression, player, 250L, HELD, PASSIVE);
        assertEquals(Map.of(SPEED, 1), after.apply());
        assertTrue(after.suppressed().isEmpty());
    }

    @Test
    void anUnrelatedSuppressionDoesNotDropOtherPassives() {
        Ability speed = potionAbility(SPEED, 1, 5); // scope 5 — NOT the one suppressed
        Snapshot snapshot = snapshot(speed);
        SuppressionStore suppression = new SuppressionStore();
        UUID player = UUID.randomUUID();
        suppression.suppress(player, CooldownStore.key(SCOPE_ENCHANT, 9), 0L, 200); // disable a different scope

        PassiveEffectDriver.Desired d = PassiveEffectDriver.computeDesired(
                worn(passive(0)), snapshot, suppression, player, 10L, HELD, PASSIVE);
        assertEquals(Map.of(SPEED, 0), d.apply(), "a debuff/disable on another enchant leaves this one intact");
    }

    @Test
    void heldAndPassiveSourcesBothContribute() {
        Ability speedPassive = potionAbility(SPEED, 1, 5);
        Ability hasteHeld = potionAbility(HASTE, 1, 6);
        Snapshot snapshot = snapshot(speedPassive, hasteHeld);
        PassiveEffectDriver.Desired d = PassiveEffectDriver.computeDesired(
                worn(passive(0), held(1)), snapshot, new SuppressionStore(), UUID.randomUUID(), 0L, HELD, PASSIVE);

        assertEquals(Map.of(SPEED, 0, HASTE, 0), d.apply());
    }

    @Test
    void aStaleWornStateYieldsNothing() {
        Snapshot snapshot = snapshot(potionAbility(SPEED, 1, 5));
        WornState stale = new WornState(GEN + 1, new BitSet(), new int[0], HeroicStat.NONE,
                byTrigger(passive(0)), new int[0], new int[0]);
        PassiveEffectDriver.Desired d = PassiveEffectDriver.computeDesired(
                stale, snapshot, new SuppressionStore(), UUID.randomUUID(), 0L, HELD, PASSIVE);
        assertTrue(d.apply().isEmpty());
        assertTrue(d.suppressed().isEmpty());
    }

    @Test
    void nonSelfPotionsAreIgnored() {
        Ability victimDebuff = potionAbility(SPEED, 1, 5, "VICTIM"); // a @Victim potion is not a self buff
        Snapshot snapshot = snapshot(victimDebuff);
        PassiveEffectDriver.Desired d = PassiveEffectDriver.computeDesired(
                worn(passive(0)), snapshot, new SuppressionStore(), UUID.randomUUID(), 0L, HELD, PASSIVE);
        assertTrue(d.apply().isEmpty(), "only @Self potions are maintained as passive buffs");
        assertFalse(d.suppressed().contains(SPEED));
    }

    // --- fixtures -------------------------------------------------------------------------------------------

    private static Ability potionAbility(int effectHandle, int level, int scopeEnchant) {
        return potionAbility(effectHandle, level, scopeEnchant, "SELF");
    }

    private static Ability potionAbility(int effectHandle, int level, int scopeEnchant, String selectorHead) {
        Args args = mock(Args.class);
        when(args.integer("effect")).thenReturn(effectHandle);
        when(args.integer("level")).thenReturn(level);
        CompiledEffect effect = mock(CompiledEffect.class);
        when(effect.head()).thenReturn("POTION");
        when(effect.target()).thenReturn(new CompiledSelector(selectorHead, Args.empty()));
        when(effect.args()).thenReturn(args);
        Ability ability = mock(Ability.class);
        when(ability.effects()).thenReturn(new CompiledEffect[]{effect});
        when(ability.cdScopeEnchant()).thenReturn(scopeEnchant);
        when(ability.cdScopeGroup()).thenReturn(-1);
        when(ability.cdScopeType()).thenReturn(-1);
        return ability;
    }

    private static Snapshot snapshot(Ability... abilities) {
        Snapshot snapshot = mock(Snapshot.class);
        when(snapshot.abilities()).thenReturn(abilities);
        when(snapshot.generation()).thenReturn(GEN);
        return snapshot;
    }

    private static WornState worn(int[]... slots) {
        return new WornState(GEN, new BitSet(), new int[0], HeroicStat.NONE, byTrigger(slots),
                new int[0], new int[0]);
    }

    private static int[][] byTrigger(int[]... slots) {
        int[][] byTrigger = new int[WIDTH][];
        Arrays.fill(byTrigger, new int[0]);
        for (int[] slot : slots) {
            byTrigger[slot[0]] = Arrays.copyOfRange(slot, 1, slot.length);
        }
        return byTrigger;
    }

    private static int[] passive(int... ids) {
        return prefixed(PASSIVE, ids);
    }

    private static int[] held(int... ids) {
        return prefixed(HELD, ids);
    }

    private static int[] prefixed(int trigger, int[] ids) {
        int[] out = new int[ids.length + 1];
        out[0] = trigger;
        System.arraycopy(ids, 0, out, 1, ids.length);
        return out;
    }
}
