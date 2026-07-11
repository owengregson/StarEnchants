package feature.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The fish-hook {@code EntityType}-NAME matcher (ADR-0053): matching is by name because the bobber's class
 * and constant were renamed across the 1.8→26.x range ({@code instanceof FishHook} / a bare constant would
 * not link everywhere). The event routing itself is live-gate territory.
 */
class ImmuneListenerTest {

    @Test
    void matchesEveryEraSpellingOfTheRodBobber() {
        assertTrue(ImmuneListener.isFishHookTypeName("FISHING_BOBBER"), "1.20.5+ spelling");
        assertTrue(ImmuneListener.isFishHookTypeName("FISHING_HOOK"), "pre-1.20.5 spelling");
        assertTrue(ImmuneListener.isFishHookTypeName("FISH_HOOK"), "legacy spelling");
    }

    @Test
    void neverClaimsOtherProjectiles() {
        // A false positive would route arrows into the FISHHOOK arm instead of PROJECTILE immunity.
        assertFalse(ImmuneListener.isFishHookTypeName("ARROW"));
        assertFalse(ImmuneListener.isFishHookTypeName("SNOWBALL"));
        assertFalse(ImmuneListener.isFishHookTypeName("fishing_bobber"), "EntityType names are upper-case");
    }
}
