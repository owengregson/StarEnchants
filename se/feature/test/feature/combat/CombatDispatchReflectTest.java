package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CombatDispatchReflectTest {

    @Test
    void baseContentKeyGroupsOnlyAbilitiesOfTheSameEnchant() {
        assertEquals("enchants/lifesteal", CombatDispatch.baseContentKey("enchants/lifesteal/5", 1));
        assertEquals("enchants/lifesteal", CombatDispatch.baseContentKey("enchants/lifesteal/5/a1", 2));
        assertEquals("enchants/poison", CombatDispatch.baseContentKey("enchants/poison/3", 3));
        assertEquals("#9", CombatDispatch.baseContentKey(null, 9));
    }
}
