package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
