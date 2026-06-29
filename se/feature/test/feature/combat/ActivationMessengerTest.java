package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.model.Ability;
import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.model.SourceKind;
import item.mint.ItemFactory;
import org.junit.jupiter.api.Test;

/**
 * Pure token-substitution tests for the message-on-activate render. The templates are test-owned inputs (not
 * the shipped strings), so they pin the substitution + colour translation without re-typing the catalogue; the
 * BY/ON routing + threading are covered live.
 */
class ActivationMessengerTest {

    @Test
    void substitutesEveryTokenAndTranslatesColours() {
        String out = ActivationMessenger.render(
                "{TIER_COLOR}{ENCHANT} ON {VICTIM} FROM {ATTACKER}", "Venom", "&e", "Bob", "Alice");
        assertEquals(ItemFactory.color("&eVenom ON Bob FROM Alice"), out);
    }

    @Test
    void toleratesTheHyphenatedTierColorToken() {
        assertEquals(ItemFactory.color("&cZap"),
                ActivationMessenger.render("{TIER-COLOR}{ENCHANT}", "Zap", "&c", "v", "a"));
    }

    @Test
    void isPassiveSuppressesSetBonusesAndFlatAlwaysOnBuffs() {
        // a set bonus never announces (it has its own equip/remove message) — even a chance-gated one
        assertTrue(ActivationMessenger.isPassive(ability(SourceKind.SET, 25.0, 100, 0)));
        // a guaranteed, cooldown-free, non-repeating buff is silent like a permanent potion enchant
        assertTrue(ActivationMessenger.isPassive(ability(SourceKind.ENCHANT, 100.0, 0, 0)));
        // but a real proc announces
        assertFalse(ActivationMessenger.isPassive(ability(SourceKind.ENCHANT, 25.0, 0, 0)), "chance proc");
        assertFalse(ActivationMessenger.isPassive(ability(SourceKind.ENCHANT, 100.0, 40, 0)), "cooldown'd");
        assertFalse(ActivationMessenger.isPassive(ability(SourceKind.ENCHANT, 100.0, 0, 20)), "repeating");
        assertFalse(ActivationMessenger.isPassive(null));
    }

    private static Ability ability(SourceKind kind, double chance, int cooldownTicks, int repeatTicks) {
        return new Ability(0, 0, kind, 1, 1, chance, cooldownTicks, 0, 0L, null, new CompiledEffect[0], repeatTicks,
                Affinity.CONTEXT_LOCAL, -1, -1, -1, -1, 0);
    }
}
