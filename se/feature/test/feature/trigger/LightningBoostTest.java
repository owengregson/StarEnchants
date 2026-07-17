package feature.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import compile.model.Ability;
import compile.model.CompiledEffect;
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
 * Unit-pins {@link LightningBoost#compute} — the pure worn LIGHTNING_MOD read (ADR-0063): sources SUM
 * (stackable Bolt crystals add), suppression drops a source for exactly its window, non-matching heads
 * are ignored, and a stale/absent worn state or an absent PASSIVE trigger yields 0.
 */
class LightningBoostTest {

    private static final int PASSIVE = 8;
    private static final int GEN = 1;
    private static final int WIDTH = 12;

    private static final int SCOPE_ENCHANT = 0; // mirrors ActivationPipeline / CooldownStore packing

    @Test
    void wornSourcesSumIntoOneFraction() {
        Snapshot snapshot = snapshot(lightningModAbility(10, 5), lightningModAbility(15, 6));
        assertEquals(0.25, LightningBoost.compute(
                worn(passive(0, 1)), snapshot, new SuppressionStore(), UUID.randomUUID(), 0L, PASSIVE),
                1e-9);
    }

    @Test
    void aSuppressedSourceContributesNothingUntilItsWindowEnds() {
        Snapshot snapshot = snapshot(lightningModAbility(10, 7));
        SuppressionStore suppression = new SuppressionStore();
        UUID player = UUID.randomUUID();
        suppression.suppress(player, CooldownStore.key(SCOPE_ENCHANT, 7), 0L, 200);

        assertEquals(0.0, LightningBoost.compute(
                worn(passive(0)), snapshot, suppression, player, 50L, PASSIVE), 1e-9);
        assertEquals(0.10, LightningBoost.compute(
                worn(passive(0)), snapshot, suppression, player, 250L, PASSIVE), 1e-9);
    }

    @Test
    void nonMatchingHeadsStaleStateAndAbsentTriggerYieldZero() {
        Snapshot snapshot = snapshot(otherAbility(6));
        assertEquals(0.0, LightningBoost.compute(
                worn(passive(0)), snapshot, new SuppressionStore(), UUID.randomUUID(), 0L, PASSIVE));

        Snapshot real = snapshot(lightningModAbility(10, 5));
        WornState stale = WornStates.worn().gen(GEN + 1).byTrigger(byTrigger(passive(0))).build();
        assertEquals(0.0, LightningBoost.compute(
                stale, real, new SuppressionStore(), UUID.randomUUID(), 0L, PASSIVE));
        assertEquals(0.0, LightningBoost.compute(
                null, real, new SuppressionStore(), UUID.randomUUID(), 0L, PASSIVE));
        assertEquals(0.0, LightningBoost.compute(
                worn(passive(0)), real, new SuppressionStore(), UUID.randomUUID(), 0L, -1));
    }

    // --- fixtures (the WaterSpeedDriverTest shapes) -----------------------------------------------------

    private static Ability lightningModAbility(double amount, int scopeEnchant) {
        Args args = mock(Args.class);
        when(args.dbl("amount")).thenReturn(amount);
        CompiledEffect effect = mock(CompiledEffect.class);
        when(effect.head()).thenReturn("LIGHTNING_MOD");
        when(effect.args()).thenReturn(args);
        return ability(effect, scopeEnchant);
    }

    private static Ability otherAbility(int scopeEnchant) {
        CompiledEffect effect = mock(CompiledEffect.class);
        when(effect.head()).thenReturn("DAMAGE_MOD");
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
        int[] row = new int[ids.length + 1];
        row[0] = PASSIVE;
        System.arraycopy(ids, 0, row, 1, ids.length);
        return row;
    }
}
