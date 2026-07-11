package feature.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import compile.model.Ability;
import compile.model.CompiledEffect;
import compile.model.CompiledSelector;
import compile.model.Snapshot;
import engine.stores.CooldownStore;
import engine.stores.SuppressionStore;
import item.worn.WornState;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import schema.spec.Args;
import testfx.Snapshots;
import testfx.WornStates;

/**
 * Unit-pins {@link MaxHealthDriver#computeExpected} — the pure re-derivation of a player's worn max-health
 * bonus (1.8.0). The load-bearing behaviours vs the old {@code HEALTH_BOOST} potion pool: sources ADD (a Nature
 * crystal under a Godly Overload can no longer be clobbered by amplifier-max), suppression drops a source for
 * exactly its window, and a stale worn state yields 0 (SET-not-add makes that safe). The modifier write itself
 * is the sink leaf's, covered live.
 */
class MaxHealthDriverTest {

    private static final int HELD = 11;
    private static final int PASSIVE = 8;
    private static final int GEN = 1;
    private static final int WIDTH = 12;

    private static final int SCOPE_ENCHANT = 0; // mirrors ActivationPipeline / CooldownStore packing

    @Test
    void wornSourcesAddTogether() {
        // The clobber fix: 4 (Nature) + 4 (Santa) = 8 — the potion pool would have taken max(level) instead.
        Snapshot snapshot = snapshot(healthAbility(4.0, 5), healthAbility(4.0, 6));
        double expected = MaxHealthDriver.computeExpected(
                worn(passive(0, 1)), snapshot, new SuppressionStore(), UUID.randomUUID(), 0L, HELD, PASSIVE);
        assertEquals(8.0, expected);
    }

    @Test
    void heldAndPassiveBothContribute() {
        Snapshot snapshot = snapshot(healthAbility(4.0, 5), healthAbility(2.0, 6));
        double expected = MaxHealthDriver.computeExpected(
                worn(passive(0), held(1)), snapshot, new SuppressionStore(), UUID.randomUUID(), 0L, HELD, PASSIVE);
        assertEquals(6.0, expected);
    }

    @Test
    void aSuppressedSourceContributesNothingUntilItsWindowEnds() {
        Snapshot snapshot = snapshot(healthAbility(4.0, 7));
        SuppressionStore suppression = new SuppressionStore();
        UUID player = UUID.randomUUID();
        suppression.suppress(player, CooldownStore.key(SCOPE_ENCHANT, 7), 0L, 200);

        assertEquals(0.0, MaxHealthDriver.computeExpected(
                worn(passive(0)), snapshot, suppression, player, 50L, HELD, PASSIVE),
                "a DISABLE'd source grants no hearts mid-window");
        assertEquals(4.0, MaxHealthDriver.computeExpected(
                worn(passive(0)), snapshot, suppression, player, 250L, HELD, PASSIVE),
                "the window's end restores the bonus");
    }

    @Test
    void nonSelfAndNonHealthEffectsAreIgnored() {
        Snapshot snapshot = snapshot(healthAbility(4.0, 5, "VICTIM"), otherAbility(6));
        double expected = MaxHealthDriver.computeExpected(
                worn(passive(0, 1)), snapshot, new SuppressionStore(), UUID.randomUUID(), 0L, HELD, PASSIVE);
        assertEquals(0.0, expected, "only HEALTH + @Self is a maintained worn bonus");
    }

    @Test
    void aStaleWornStateYieldsZero() {
        Snapshot snapshot = snapshot(healthAbility(4.0, 5));
        WornState stale = WornStates.worn().gen(GEN + 1).byTrigger(byTrigger(passive(0))).build();
        assertEquals(0.0, MaxHealthDriver.computeExpected(
                stale, snapshot, new SuppressionStore(), UUID.randomUUID(), 0L, HELD, PASSIVE));
    }

    // --- fixtures (the PassiveEffectDriverTest shapes) --------------------------------------------------------

    private static Ability healthAbility(double amount, int scopeEnchant) {
        return healthAbility(amount, scopeEnchant, "SELF");
    }

    private static Ability healthAbility(double amount, int scopeEnchant, String selectorHead) {
        Args args = mock(Args.class);
        when(args.dbl("amount")).thenReturn(amount);
        CompiledEffect effect = mock(CompiledEffect.class);
        when(effect.head()).thenReturn("HEALTH");
        when(effect.target()).thenReturn(new CompiledSelector(selectorHead, Args.empty()));
        when(effect.args()).thenReturn(args);
        return ability(effect, scopeEnchant);
    }

    private static Ability otherAbility(int scopeEnchant) {
        CompiledEffect effect = mock(CompiledEffect.class);
        when(effect.head()).thenReturn("DAMAGE_MOD");
        when(effect.target()).thenReturn(new CompiledSelector("SELF", Args.empty()));
        return ability(effect, scopeEnchant);
    }

    private static Ability ability(CompiledEffect effect, int scopeEnchant) {
        Ability ability = mock(Ability.class);
        when(ability.effects()).thenReturn(new CompiledEffect[]{effect});
        when(ability.cdScopeEnchant()).thenReturn(scopeEnchant);
        when(ability.cdScopeGroup()).thenReturn(-1);
        when(ability.cdScopeType()).thenReturn(-1);
        return ability;
    }

    private static Snapshot snapshot(Ability... abilities) {
        return Snapshots.snapshot().abilities(abilities).generation(GEN).build();
    }

    private static WornState worn(int[]... slots) {
        return WornStates.worn().gen(GEN).byTrigger(byTrigger(slots)).build();
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

    private static int[] prefixed(int trigger, int... ids) {
        int[] slot = new int[ids.length + 1];
        slot[0] = trigger;
        System.arraycopy(ids, 0, slot, 1, ids.length);
        return slot;
    }
}
