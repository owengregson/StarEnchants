package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.def.AbilityDef;
import compile.def.RebateKnobs;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.Test;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;

/**
 * The chance-rebate envelope at LOAD time (ADR-0076 part E): scoped inheritance, the recipient enum, and the
 * two shapes that are rejected because both would ship as content that quietly does nothing.
 */
class RebateKnobsReaderTest {

    private static IntSupplier counter() {
        int[] id = {0};
        return () -> id[0]++;
    }

    private static AbilityDef readOne(String yaml, Diagnostics diags) {
        EnchantDefReader.Parsed parsed = EnchantDefReader.read("enchants/trapish",
                YamlNode.compose("test.yml", yaml, diags), counter(), diags);
        return parsed.abilities().get(0);
    }

    private static void assertCode(Diagnostics diags, DiagCode code) {
        assertTrue(diags.all().stream().anyMatch(d -> d.is(code)), () -> diags.all().toString());
    }

    @Test
    void eachKnobInheritsFromTheInnermostScopeThatDeclaresIt() {
        // The soul envelope's rule, applied here: a file-root line with per-level terms is the shape every
        // migrated consumer authors, and a per-level override must beat the root rather than merge with it.
        Diagnostics diags = new Diagnostics();
        AbilityDef ability = readOne("""
            display: Trapish
            trigger: ATTACK
            applies-to: [SWORD]
            blocked-message: "root line"
            rebate-spends-cooldown: true
            levels:
              1:
                chance: 4
                chance-rebate: "2.5 * %victim.enchlevel.metaphysical%"
                rebate-spends-cooldown: false
                effects: [{ MESSAGE: { text: hi, who: "@Self" } }]
            """, diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        RebateKnobs knobs = ability.rebate();
        assertEquals("2.5 * %victim.enchlevel.metaphysical%", knobs.points());
        assertEquals("root line", knobs.message());
        assertFalse(knobs.spendsCooldown(), "the inner false must beat the outer true");
        assertFalse(knobs.messageToActor(), "the victim is the default recipient");
    }

    @Test
    void bothTermsTogetherAreRejected() {
        Diagnostics diags = new Diagnostics();
        readOne("""
            display: Trapish
            trigger: ATTACK
            applies-to: [SWORD]
            levels:
              1:
                chance: 4
                chance-rebate: "1"
                chance-rebate-scale: "0.5"
                effects: [{ MESSAGE: { text: hi, who: "@Self" } }]
            """, diags);
        assertCode(diags, DiagCode.E_LOAD_REBATE);
    }

    @Test
    void feedbackWithNoTermIsRejectedRatherThanShippedInert() {
        // A blocked-message with nothing to block on is a line the author will never see fire, and the file
        // reads as though it does — the exact failure the whole verdict exists to make impossible.
        Diagnostics diags = new Diagnostics();
        AbilityDef ability = readOne("""
            display: Trapish
            trigger: ATTACK
            applies-to: [SWORD]
            levels:
              1:
                chance: 4
                blocked-message: "never fires"
                effects: [{ MESSAGE: { text: hi, who: "@Self" } }]
            """, diags);
        assertCode(diags, DiagCode.E_LOAD_REBATE);
        assertNull(ability.rebate().message(), "the orphaned envelope is dropped whole");
    }

    @Test
    void anUnknownRecipientIsADiagnosticNotASilentDefault() {
        Diagnostics diags = new Diagnostics();
        readOne("""
            display: Trapish
            trigger: ATTACK
            applies-to: [SWORD]
            levels:
              1:
                chance: 4
                chance-rebate: "1"
                blocked-message: "line"
                blocked-message-who: bystander
                effects: [{ MESSAGE: { text: hi, who: "@Self" } }]
            """, diags);
        assertCode(diags, DiagCode.E_LOAD_REBATE);
    }

    @Test
    void theActorRecipientFlipsTheDefault() {
        Diagnostics diags = new Diagnostics();
        AbilityDef ability = readOne("""
            display: Trapish
            trigger: ATTACK
            applies-to: [SWORD]
            levels:
              1:
                chance: 4
                chance-rebate: "1"
                blocked-message: "line"
                blocked-message-who: actor
                effects: [{ MESSAGE: { text: hi, who: "@Self" } }]
            """, diags);
        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertTrue(ability.rebate().messageToActor());
    }

    @Test
    void anAbilityWithNoEnvelopeCarriesTheAbsentOne() {
        Diagnostics diags = new Diagnostics();
        AbilityDef ability = readOne("""
            display: Trapish
            trigger: ATTACK
            applies-to: [SWORD]
            levels:
              1: { chance: 4, effects: [{ MESSAGE: { text: hi, who: "@Self" } }] }
            """, diags);
        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals(RebateKnobs.NONE, ability.rebate());
    }
}
