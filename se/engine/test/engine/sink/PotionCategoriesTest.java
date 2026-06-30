package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;

/** Pure classification of a potion effect's category by canonical name — version-spelling-agnostic (no server). */
class PotionCategoriesTest {

    private static PotionEffectType named(String name) {
        PotionEffectType type = mock(PotionEffectType.class);
        when(type.getName()).thenReturn(name);
        return type;
    }

    @Test
    void classifiesHarmfulByEitherSpellingAcrossTheRenameFlip() {
        assertEquals(PotionCategories.HARMFUL, PotionCategories.categoryOf(named("POISON")));
        assertEquals(PotionCategories.HARMFUL, PotionCategories.categoryOf(named("SLOW")));          // pre-1.20.5
        assertEquals(PotionCategories.HARMFUL, PotionCategories.categoryOf(named("SLOWNESS")));      // post-1.20.5
        assertEquals(PotionCategories.HARMFUL, PotionCategories.categoryOf(named("HARM")));          // pre-1.20.5
        assertEquals(PotionCategories.HARMFUL, PotionCategories.categoryOf(named("INSTANT_DAMAGE"))); // post-1.20.5
    }

    @Test
    void classifiesBeneficialAndFallsBackToNeutral() {
        assertEquals(PotionCategories.BENEFICIAL, PotionCategories.categoryOf(named("REGENERATION")));
        assertEquals(PotionCategories.BENEFICIAL, PotionCategories.categoryOf(named("SPEED")));
        assertEquals(PotionCategories.NEUTRAL, PotionCategories.categoryOf(named("GLOWING")));
        assertEquals(PotionCategories.NEUTRAL, PotionCategories.categoryOf(null));
    }

    @Test
    void matchesAllAlwaysElseByCategory() {
        PotionEffectType poison = named("POISON");
        assertTrue(PotionCategories.matches(PotionCategories.ALL, poison));         // ALL clears everything
        assertTrue(PotionCategories.matches(PotionCategories.HARMFUL, poison));     // Bless clears the debuff
        assertFalse(PotionCategories.matches(PotionCategories.BENEFICIAL, poison)); // a buff-only cure leaves it
    }
}
