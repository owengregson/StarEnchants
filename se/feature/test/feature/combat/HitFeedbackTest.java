package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The per-hit feedback number format: a chat readout, so trailing zeros go and the locale never leaks. */
class HitFeedbackTest {

    @ParameterizedTest(name = "{0} → \"{1}\"")
    @CsvSource({
            "5.0, 5",
            "2.5, 2.5",
            "2.505, 2.51",
            "0.004, 0",
            "12.30, 12.3",
    })
    void damageRendersAtMostTwoDecimalsWithNoTrailingZero(double damage, String rendered) {
        assertEquals("hit for " + rendered, HitFeedback.fill("hit for {damage}", damage));
    }

    @org.junit.jupiter.api.Test
    void aTemplateWithoutTheTokenIsPassedThrough() {
        assertEquals("plain", HitFeedback.fill("plain", 3.0));
    }
}
