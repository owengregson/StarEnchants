package feature.useitem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import compile.load.ContentHolder;
import compile.load.UseItemDef;
import engine.run.UseAttempt;
import feature.trigger.TriggerDispatch;
import item.codec.UseItemCodec;
import item.mint.VanillaEnchants;
import java.util.List;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * The §3.6 use-item outcome collapse: {@link UseItemService#use} maps the engine's {@link UseAttempt} to a
 * {@link UseOutcome} by the fixed precedence and — the load-bearing part — names the FAILED condition by the
 * candidate index the engine reported, aligned to the def's authored ability order. The mint/listener glue is
 * Bukkit-backed (covered by the live UseItemSuite); this pins the pure mapping with a stubbed dispatch.
 */
class UseItemServiceTest {

    // Two abilities: a0 with no condition, a1 with an authored one — so a condition stop at index 1 must name a1's.
    private static final UseItemDef DEF = new UseItemDef("rage", "&cRage", "RED_DYE", List.of(),
            true, false, "", 1200, List.of("", "%health% < 6"), List.of("use:rage", "use:rage/a1"));

    private UseItemService serviceReturning(UseAttempt attempt) {
        TriggerDispatch dispatch = mock(TriggerDispatch.class);
        Player player = mock(Player.class);
        when(dispatch.fireUse(player, DEF.stableKeys())).thenReturn(attempt);
        UseItemService service = new UseItemService(mock(ContentHolder.class), mock(UseItemCodec.class),
                dispatch, VanillaEnchants.NONE);
        this.player = player;
        return service;
    }

    private Player player;

    @Test
    void anyActivatedCandidateWins() {
        UseOutcome out = serviceReturning(new UseAttempt(true, true, 40, 1, true)).use(player, DEF);
        assertEquals(UseOutcome.Result.ACTIVATED, out.result());
    }

    @Test
    void cooldownBeatsConditionAndChanceAndCarriesRemaining() {
        UseOutcome out = serviceReturning(new UseAttempt(false, true, 40, 1, true)).use(player, DEF);
        assertEquals(UseOutcome.Result.ON_COOLDOWN, out.result());
        assertEquals(40L, out.cooldownRemainingTicks());
    }

    @Test
    void conditionStopNamesTheFailingAbilitysAuthoredSource() {
        UseOutcome out = serviceReturning(new UseAttempt(false, false, 0, 1, true)).use(player, DEF);
        assertEquals(UseOutcome.Result.CONDITION_FAILED, out.result());
        assertEquals("%health% < 6", out.conditionSource()); // index 1 → a1's condition, not a0's ""
    }

    @Test
    void chanceFailIsSilentButDistinctFromBlocked() {
        assertEquals(UseOutcome.Result.CHANCE_FAILED,
                serviceReturning(new UseAttempt(false, false, 0, -1, true)).use(player, DEF).result());
    }

    @Test
    void noSignalIsBlocked() {
        assertEquals(UseOutcome.Result.BLOCKED,
                serviceReturning(UseAttempt.none()).use(player, DEF).result());
    }

    @Test
    void anOutOfRangeConditionIndexFallsBackToEmptyNotAnException() {
        UseOutcome out = serviceReturning(new UseAttempt(false, false, 0, 9, false)).use(player, DEF);
        assertEquals(UseOutcome.Result.CONDITION_FAILED, out.result());
        assertEquals("", out.conditionSource());
    }
}
