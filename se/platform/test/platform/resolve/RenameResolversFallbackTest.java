package platform.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import schema.spec.HandleCategory;

/**
 * The platform-specific {@code fallbackAliases} hook: lossy degradations (e.g. the 1.8 lane) merge ON TOP of
 * the shared {@link Aliases} renames for resolution only, without entering {@link Aliases} (which the migrator
 * reuses). Mirrors how the legacy {@code RegistrySupport} maps {@code SOUL}&rarr;{@code SMOKE_LARGE}.
 */
class RenameResolversFallbackTest {

    /** A resolver where SOUL is absent but its fallback SMOKE_LARGE (and FLAME) exist, with the hook wired. */
    private static RenameResolvers legacyish() {
        return new RenameResolvers() {
            @Override
            protected boolean exists(HandleCategory category, String name) {
                return category == HandleCategory.PARTICLE && Set.of("SMOKE_LARGE", "FLAME").contains(name);
            }

            @Override
            protected Map<String, String> fallbackAliases(HandleCategory category) {
                return category == HandleCategory.PARTICLE ? Map.of("SOUL", "SMOKE_LARGE") : Map.of();
            }
        };
    }

    @Test
    void fallbackAliasDegradesAnUnavailableTokenToItsClosestEquivalent() {
        RenameResolvers r = legacyish();
        int id = r.particle("SOUL").orElseThrow();
        assertEquals("SMOKE_LARGE", r.nameOf(HandleCategory.PARTICLE, id));
    }

    @Test
    void aDirectlyAvailableTokenStillWinsOverItsFallback() {
        RenameResolvers r = legacyish();
        int id = r.particle("FLAME").orElseThrow();
        assertEquals("FLAME", r.nameOf(HandleCategory.PARTICLE, id));
    }

    @Test
    void withoutAFallbackAnUnavailableTokenIsEmpty() {
        RenameResolvers plain = new RenameResolvers() {
            @Override
            protected boolean exists(HandleCategory category, String name) {
                return false;
            }
        };
        assertTrue(plain.particle("SOUL").isEmpty());
    }
}
