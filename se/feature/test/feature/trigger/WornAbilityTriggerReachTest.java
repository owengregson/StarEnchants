package feature.trigger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.model.Ability;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import item.codec.HeroicStat;
import item.worn.WornFlattener;
import item.worn.WornState;
import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import testfx.Abilities;

/**
 * The #286 safety net: an ability that lives on WORN gear still reaches the triggers whose
 * {@code TriggerKind.usesHeld()} flag says "read the ability from the held item only".
 *
 * <p>That flag is documentation — {@link WornFlattener} routes by trigger mask and combat DIRECTION and
 * nothing else, so armour and hands merge into one candidate list. Several shipped entries depend on it:
 * Nutrition is worn leggings on {@code EAT}, the mask/set families hang bonuses on {@code INTERACT}/
 * {@code USE}, and Repair Guard reads {@code ITEM_DAMAGE} (already re-declared NEUTRAL for exactly this
 * reason, ADR-0049). A future "the flag should mean something" cleanup would silently make every one of
 * them inert with no test failing anywhere — this is that failure.
 */
class WornAbilityTriggerReachTest {

    private static final TriggerRegistry TRIGGERS = BuiltinTriggers.registry();

    /**
     * Every trigger a worn ability is expected to reach: the held-flagged family the deferral ledger names
     * plus the two neutral ones that already carry worn consumers. Driven off the registry's own ids so a
     * rename is a compile-time miss here rather than a silent gap.
     */
    private static final List<String> WORN_REACHABLE =
            List.of("EAT", "INTERACT", "INTERACT_LEFT", "INTERACT_RIGHT", "USE", "BREAK", "HELD",
                    "FISHING", "ITEM_DAMAGE", "MINE");

    @TestFactory
    List<DynamicTest> wornAbilitiesWalkHeldFlaggedTriggers() {
        return WORN_REACHABLE.stream().map(name -> DynamicTest.dynamicTest(
                "a worn ability reaches " + name, () -> {
                    int trigger = TRIGGERS.idOf(name).orElseThrow();
                    // Ability id 4 is "worn": the flattener is handed it as a MAIN id (armour + main hand
                    // merge there), which is the only provenance a worn enchant ever has.
                    Ability worn = Abilities.ability().id(4).triggerMask(1 << trigger).build();
                    Ability[] abilities = new Ability[5];
                    abilities[4] = worn;

                    WornState state = WornFlattener.flatten(1, new int[]{4}, abilities, TRIGGERS.count(),
                            new BitSet(), new int[0], HeroicStat.NONE,
                            TRIGGERS.attackTriggers(), TRIGGERS.defenseTriggers());

                    assertArrayEquals(new int[]{4}, state.byTrigger(trigger),
                            name + " dropped a worn candidate — held-only routing was made real");
                    // None of these is a combat direction, so the ability must not leak into either
                    // combat array (which would run it on every swing as well).
                    assertArrayEquals(new int[0], state.combatAttack());
                    assertArrayEquals(new int[0], state.combatDefense());
                })).toList();
    }

    /** EAT is the entry R-QC42 re-declared; pin that the declaration now matches the routing. */
    @org.junit.jupiter.api.Test
    void eatDeclaresTheEquipmentScanItActuallyPerforms() {
        var eat = TRIGGERS.byId(TRIGGERS.idOf("EAT").orElseThrow());
        assertTrue(eat.scansEquipment(), "Nutrition is worn leggings; EAT must declare the worn scan");
    }
}
