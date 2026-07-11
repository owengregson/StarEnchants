package bootstrap.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The rage stable-key contract: content keys are {@code <source>/<stem>} (LibraryLoader.keyTierOf), so the worn
 * lookup must match {@code enchants/rage/<level>}. A bare {@code rage/<level>} prefix silently resolved 0 and
 * turned the whole rage system (action bar, blaze cue, {@code %ragestacks%} damage) inert on live servers.
 */
final class CombatModuleRageKeyTest {

    private static int level(Map<Integer, String> keys, int... aids) {
        return CombatModule.rageLevel(aids, keys::get, aid -> Integer.parseInt(keys.get(aid).substring(keys.get(aid).lastIndexOf('/') + 1)));
    }

    @Test
    void resolvesTheRealSourcePrefixedKeyFormat() {
        assertEquals(6, level(Map.of(1, "enchants/rage/6"), 1));
    }

    @Test
    void picksTheHighestRageAmongWornAbilities() {
        assertEquals(4, level(Map.of(1, "enchants/rage/2", 2, "enchants/insanity/8", 3, "enchants/rage/4"), 1, 2, 3));
    }

    @Test
    void theBareKeyFormatThatShippedBrokenResolvesNothing() {
        assertEquals(0, level(Map.of(1, "rage/6"), 1)); // the pre-fix bug: a prefix without the source segment
    }

    @Test
    void ignoresNonRageAndPrefixCousins() {
        assertEquals(0, level(Map.of(1, "enchants/enraged/3", 2, "enchants/silence/4"), 1, 2));
    }

    @Test
    void nullKeysAndEmptyWornStateResolveZero() {
        assertEquals(0, CombatModule.rageLevel(new int[] {7}, aid -> null, aid -> 9));
        assertEquals(0, CombatModule.rageLevel(new int[0], aid -> "enchants/rage/6", aid -> 6));
    }
}
