package compile.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlatformResolversTest {

    @Test
    void fakeResolvesKnownTokensCaseInsensitively() {
        PlatformResolvers r = FakeResolvers.builder()
                .material("DIAMOND_SWORD", 5)
                .enchantment("SHARPNESS", 9)
                .build();
        assertEquals(5, r.material("diamond_sword").orElseThrow());
        assertEquals(9, r.enchantment("SHARPNESS").orElseThrow());
    }

    @Test
    void unmappedTokensResolveToEmptyForWarnAndSkip() {
        PlatformResolvers r = FakeResolvers.builder().material("STONE", 1).build();
        assertTrue(r.material("unobtanium").isEmpty());
        assertTrue(r.sound("ANYTHING").isEmpty());
        assertTrue(r.attribute("GENERIC_MAX_HEALTH").isEmpty());
    }
}
