package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class RotAndDecayListenerTest {

    @Test
    void decayStacksDealTheExactTwoHpPerStackLadder() {
        for (int stacks = 1; stacks <= 6; stacks++) {
            assertEquals(stacks * 2.0, RotAndDecayListener.decayDamage(stacks));
        }
    }
}
