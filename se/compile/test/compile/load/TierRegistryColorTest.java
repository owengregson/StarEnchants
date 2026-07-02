package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import schema.diag.Diagnostics;

/** {@link TierRegistry#colorOf} — the single tier-colour rule (registered colour, else the {@code &7} default). */
final class TierRegistryColorTest {

    @Test
    void registeredTierYieldsItsColour() {
        assertEquals("&7", TierRegistry.BUILTIN.colorOf("common"));
        assertEquals("&d", TierRegistry.BUILTIN.colorOf("epic"));
    }

    @Test
    void nullOrUnregisteredTierIsGrey() {
        assertEquals("&7", TierRegistry.BUILTIN.colorOf(null));
        assertEquals("&7", TierRegistry.BUILTIN.colorOf("nope"));
    }

    @Test
    void blankColourFallsBackToGrey() {
        Diagnostics diags = new Diagnostics();
        TierRegistry tiers = TierRegistry.read(
                YamlNode.compose("tiers.yml", "tiers:\n  plain:\n    color: \"\"\n    weight: 10\n", diags), diags);
        assertEquals("&7", tiers.colorOf("plain"));
    }
}
