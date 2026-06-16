package platform.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import schema.spec.HandleCategory;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VocabularyResolversTest {

    private static VocabularyResolvers resolvers() {
        return new VocabularyResolvers(Map.of(
                HandleCategory.POTION_EFFECT, Set.of("NAUSEA", "STRENGTH"),
                HandleCategory.MATERIAL, Set.of("GUNPOWDER"),
                HandleCategory.ATTRIBUTE, Set.of("MAX_HEALTH")));
    }

    @Test
    void resolvesLegacyTokenToTheModernHandleAndInternsIt() {
        VocabularyResolvers r = resolvers();
        OptionalInt id = r.potionEffect("CONFUSION"); // legacy → NAUSEA
        assertTrue(id.isPresent());
        assertEquals("NAUSEA", r.nameOf(HandleCategory.POTION_EFFECT, id.getAsInt()));
    }

    @Test
    void sameResolvedHandleSharesOneInternedId() {
        VocabularyResolvers r = resolvers();
        int viaLegacy = r.potionEffect("CONFUSION").getAsInt();
        int viaModern = r.potionEffect("NAUSEA").getAsInt();
        int repeat = r.potionEffect("confusion").getAsInt();
        assertEquals(viaLegacy, viaModern); // both denote NAUSEA
        assertEquals(viaLegacy, repeat);    // interning is stable
    }

    @Test
    void resolvesAcrossCategories() {
        VocabularyResolvers r = resolvers();
        assertEquals("GUNPOWDER", r.nameOf(HandleCategory.MATERIAL, r.material("SULPHUR").getAsInt()));
        assertEquals("MAX_HEALTH", r.nameOf(HandleCategory.ATTRIBUTE, r.attribute("GENERIC_MAX_HEALTH").getAsInt()));
    }

    @Test
    void unknownTokenIsEmpty() {
        assertTrue(resolvers().material("NONEXISTENT").isEmpty());
    }

    @Test
    void categoryWithNoVocabularyResolvesNothing() {
        assertTrue(resolvers().enchantment("SHARPNESS").isEmpty()); // ENCHANTMENT vocabulary not provided
    }
}
