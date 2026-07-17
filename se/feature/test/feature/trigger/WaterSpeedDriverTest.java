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
 * Unit-pins {@link WaterSpeedDriver#computeExpected} — the pure re-derivation of a player's worn
 * water-movement bonus (ADR-0060): sources ADD, the sum clamps to the attribute's [0,1] domain,
 * suppression drops a source for exactly its window, and a stale worn state yields 0. The modifier write
 * itself is the sink leaf's, covered live (PetAbilitySuite).
 */
class WaterSpeedDriverTest {

    private static final int HELD = 11;
    private static final int PASSIVE = 8;
    private static final int GEN = 1;
    private static final int WIDTH = 12;

    private static final int SCOPE_ENCHANT = 0; // mirrors ActivationPipeline / CooldownStore packing

    @Test
    void wornSourcesAddAndClampToTheAttributeDomain() {
        Snapshot snapshot = snapshot(waterSpeedAbility(0.09, 5), waterSpeedAbility(0.26, 6));
        assertEquals(0.35, WaterSpeedDriver.computeExpected(
                worn(passive(0, 1)), snapshot, new SuppressionStore(), UUID.randomUUID(), 0L, HELD, PASSIVE),
                1e-9);

        Snapshot over = snapshot(waterSpeedAbility(0.7, 5), waterSpeedAbility(0.7, 6));
        assertEquals(1.0, WaterSpeedDriver.computeExpected(
                worn(passive(0, 1)), over, new SuppressionStore(), UUID.randomUUID(), 0L, HELD, PASSIVE),
                1e-9, "the vanilla attribute domain is [0,1] — the sum clamps");
    }

    @Test
    void aSuppressedSourceContributesNothingUntilItsWindowEnds() {
        Snapshot snapshot = snapshot(waterSpeedAbility(0.2, 7));
        SuppressionStore suppression = new SuppressionStore();
        UUID player = UUID.randomUUID();
        suppression.suppress(player, CooldownStore.key(SCOPE_ENCHANT, 7), 0L, 200);

        assertEquals(0.0, WaterSpeedDriver.computeExpected(
                worn(passive(0)), snapshot, suppression, player, 50L, HELD, PASSIVE), 1e-9);
        assertEquals(0.2, WaterSpeedDriver.computeExpected(
                worn(passive(0)), snapshot, suppression, player, 250L, HELD, PASSIVE), 1e-9);
    }

    @Test
    void nonWaterSpeedEffectsAreIgnoredAndAStaleWornStateYieldsZero() {
        Snapshot snapshot = snapshot(otherAbility(6));
        assertEquals(0.0, WaterSpeedDriver.computeExpected(
                worn(passive(0)), snapshot, new SuppressionStore(), UUID.randomUUID(), 0L, HELD, PASSIVE));

        Snapshot real = snapshot(waterSpeedAbility(0.2, 5));
        WornState stale = WornStates.worn().gen(GEN + 1).byTrigger(byTrigger(passive(0))).build();
        assertEquals(0.0, WaterSpeedDriver.computeExpected(
                stale, real, new SuppressionStore(), UUID.randomUUID(), 0L, HELD, PASSIVE));
    }

    // --- fixtures (the MaxHealthDriverTest shapes) ------------------------------------------------------

    private static Ability waterSpeedAbility(double efficiency, int scopeEnchant) {
        Args args = mock(Args.class);
        when(args.dbl("efficiency")).thenReturn(efficiency);
        CompiledEffect effect = mock(CompiledEffect.class);
        when(effect.head()).thenReturn("WATER_SPEED");
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
        int[] row = new int[ids.length + 1];
        row[0] = PASSIVE;
        System.arraycopy(ids, 0, row, 1, ids.length);
        return row;
    }
}
