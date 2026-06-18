package feature.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import compile.load.ContentHolder;
import engine.run.AbilityExecutor;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import feature.soul.SoulBinding;
import item.worn.WornStateStore;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import platform.resolve.RuntimeHandles;

/**
 * Pins that {@link TriggerDispatch} resolves every trigger-id field to the matching id in the built-in
 * trigger vocabulary. The risk this guards is a silent one: a typo'd {@code idOf("...")} resolves to
 * {@code -1}, and {@link TriggerDispatch#fire} treats {@code -1} as a no-op — so the trigger's listener
 * would register and run but never actually fire any ability, with no error. Asserting each field equals
 * the registry's lookup catches that before it ships.
 */
class TriggerDispatchWiringTest {

    @Test
    void everyTriggerFieldResolvesToItsVocabularyId() {
        TriggerRegistry triggers = BuiltinTriggers.registry();
        Function<Player, Optional<SoulBinding>> noSouls = player -> Optional.empty();
        TriggerDispatch dispatch = new TriggerDispatch(
                mock(AbilityExecutor.class), mock(RuntimeHandles.class), mock(ContentHolder.class),
                mock(WornStateStore.class), triggers, () -> 0L, noSouls);

        assertEquals(id(triggers, "MINE"), dispatch.mine);
        assertEquals(id(triggers, "KILL"), dispatch.kill);
        assertEquals(id(triggers, "FALL"), dispatch.fall);
        assertEquals(id(triggers, "FIRE"), dispatch.fire);
        assertEquals(id(triggers, "INTERACT"), dispatch.interact);
        assertEquals(id(triggers, "INTERACT_LEFT"), dispatch.interactLeft);
        assertEquals(id(triggers, "INTERACT_RIGHT"), dispatch.interactRight);
        // The v3.2 additions — these are the ones a typo would silently disable.
        assertEquals(id(triggers, "DEATH"), dispatch.death);
        assertEquals(id(triggers, "BOW_FIRE"), dispatch.bowFire);
        assertEquals(id(triggers, "FISHING"), dispatch.fishing);
        assertEquals(id(triggers, "EAT"), dispatch.eat);
        assertEquals(id(triggers, "ITEM_DAMAGE"), dispatch.itemDamage);
        assertEquals(id(triggers, "BREAK"), dispatch.breakItem);
        assertEquals(id(triggers, "REPEATING"), dispatch.repeating); // §B repeating lifecycle

        // None of them silently fell back to the −1 no-op.
        for (int trigger : new int[] {dispatch.death, dispatch.bowFire, dispatch.fishing,
                dispatch.eat, dispatch.itemDamage, dispatch.breakItem, dispatch.repeating}) {
            assertTrue(trigger >= 0, "a v3.2 trigger resolved to the -1 no-op");
        }
    }

    private static int id(TriggerRegistry triggers, String name) {
        return triggers.idOf(name).orElseThrow(() -> new AssertionError("no trigger " + name));
    }
}
