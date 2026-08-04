package feature.mask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import compile.model.Ability;
import compile.model.CompiledEffect;
import compile.model.Snapshot;
import engine.stores.CooldownStore;
import engine.stores.SuppressionStore;
import feature.mask.SpawnerYieldListener.Grant;
import item.worn.WornState;
import java.util.Arrays;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import schema.spec.Args;
import testfx.Snapshots;
import testfx.WornStates;

/**
 * Unit-pins {@link SpawnerYieldListener#grantOf} — the pure worn SPAWNER_YIELD read: grants do NOT sum (the
 * strongest expected yield wins, so a crowd of wearers cannot multiply one spawner), suppression drops a
 * source for exactly its window, a {@code radius} grant measures while a {@code chunk} grant does not, and a
 * stale/absent worn state or an absent PASSIVE trigger yields nothing.
 */
class SpawnerYieldListenerTest {

    private static final int PASSIVE = 8;
    private static final int GEN = 1;
    private static final int WIDTH = 12;
    private static final int SCOPE_ENCHANT = 0; // mirrors ActivationPipeline / CooldownStore packing

    private static final World WORLD = mock(World.class);
    private static final Location SPAWN = new Location(WORLD, 0, 64, 0);

    @Test
    void theStrongestGrantWinsRatherThanTheSourcesSumming() {
        Snapshot snapshot = snapshot(yieldGrant(65, 1, "chunk", 0, 5), yieldGrant(50, 2, "chunk", 0, 6));
        Grant best = grantOf(worn(passive(0, 1)), snapshot, new SuppressionStore(), 0L, SPAWN);
        // 50 x 2 = 100 beats 65 x 1 = 65; both terms ride, so a chance/extra transposition cannot pass.
        assertEquals(50.0, best.chancePercent());
        assertEquals(2, best.extra());
    }

    @Test
    void aSuppressedSourceGrantsNothingUntilItsWindowEnds() {
        Snapshot snapshot = snapshot(yieldGrant(65, 1, "chunk", 0, 7));
        SuppressionStore suppression = new SuppressionStore();
        UUID player = UUID.randomUUID();
        suppression.suppress(player, CooldownStore.key(SCOPE_ENCHANT, 7), 0L, 200);

        assertEquals(0, SpawnerYieldListener.grantOf(worn(passive(0)), snapshot, suppression, player, 50L,
                PASSIVE, SPAWN, SPAWN).extra());
        assertEquals(1, SpawnerYieldListener.grantOf(worn(passive(0)), snapshot, suppression, player, 250L,
                PASSIVE, SPAWN, SPAWN).extra());
    }

    @Test
    void radiusScopeMeasuresWhileChunkScopeDoesNot() {
        Location farOff = new Location(WORLD, 40, 64, 0);
        Snapshot radiusGrant = snapshot(yieldGrant(65, 1, "radius", 16, 5));
        assertEquals(1, SpawnerYieldListener.grantOf(worn(passive(0)), radiusGrant, new SuppressionStore(),
                UUID.randomUUID(), 0L, PASSIVE, SPAWN, new Location(WORLD, 10, 64, 0)).extra());
        assertEquals(0, SpawnerYieldListener.grantOf(worn(passive(0)), radiusGrant, new SuppressionStore(),
                UUID.randomUUID(), 0L, PASSIVE, SPAWN, farOff).extra(), "40 blocks is outside a radius of 16");

        Snapshot chunkGrant = snapshot(yieldGrant(65, 1, "chunk", 0, 5));
        assertEquals(1, SpawnerYieldListener.grantOf(worn(passive(0)), chunkGrant, new SuppressionStore(),
                UUID.randomUUID(), 0L, PASSIVE, SPAWN, farOff).extra(),
                "chunk scope is decided by the caller's own chunk walk, never by distance");
    }

    @Test
    void nonMatchingHeadsStaleStateAndAbsentTriggerYieldNothing() {
        assertEquals(0, grantOf(worn(passive(0)), snapshot(otherAbility(6)), new SuppressionStore(), 0L,
                SPAWN).extra());

        Snapshot real = snapshot(yieldGrant(65, 1, "chunk", 0, 5));
        WornState stale = WornStates.worn().gen(GEN + 1).byTrigger(byTrigger(passive(0))).build();
        assertEquals(0, grantOf(stale, real, new SuppressionStore(), 0L, SPAWN).extra());
        assertEquals(0, grantOf(null, real, new SuppressionStore(), 0L, SPAWN).extra());
        assertEquals(0, SpawnerYieldListener.grantOf(worn(passive(0)), real, new SuppressionStore(),
                UUID.randomUUID(), 0L, -1, SPAWN, SPAWN).extra());
    }

    // --- fixtures (the LightningBoostTest shapes) -------------------------------------------------------

    private static Grant grantOf(WornState state, Snapshot snapshot, SuppressionStore suppression, long now,
                                 Location spawn) {
        return SpawnerYieldListener.grantOf(state, snapshot, suppression, UUID.randomUUID(), now, PASSIVE,
                spawn, spawn);
    }

    private static Ability yieldGrant(double chance, int extra, String scope, double radius, int scopeEnchant) {
        Args args = mock(Args.class);
        when(args.dbl("chance")).thenReturn(chance);
        when(args.integer("extra")).thenReturn(extra);
        when(args.str("scope")).thenReturn(scope);
        when(args.dbl("radius")).thenReturn(radius);
        CompiledEffect effect = mock(CompiledEffect.class);
        when(effect.head()).thenReturn("SPAWNER_YIELD");
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
