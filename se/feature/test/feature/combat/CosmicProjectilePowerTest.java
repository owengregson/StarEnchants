package feature.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CosmicProjectilePowerTest {

    @AfterEach
    void reset() {
        CosmicProjectilePower.clear();
    }

    @Test
    void onlyDrawsBelowSeventyFivePercentAreWeak() {
        UUID weak = UUID.randomUUID();
        UUID boundary = UUID.randomUUID();
        UUID full = UUID.randomUUID();

        CosmicProjectilePower.record(weak, 0.7499F);
        CosmicProjectilePower.record(boundary, 0.75F);
        CosmicProjectilePower.record(full, 1.0F);

        assertTrue(CosmicProjectilePower.weak(weak));
        assertFalse(CosmicProjectilePower.weak(boundary));
        assertFalse(CosmicProjectilePower.weak(full));

        CosmicProjectilePower.forget(weak);
        assertFalse(CosmicProjectilePower.weak(weak));
    }
}
