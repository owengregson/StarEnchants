package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import schema.diag.Diagnostics;

/** Parsing the unified {@code { sound: NAME, volume: V, pitch: P }} bracket form (and lists of it). */
class SoundCueTest {

    private static YamlNode yaml(String body, Diagnostics diags) {
        return YamlNode.compose("test.yml", body, diags);
    }

    @Test
    void readsTheBracketForm() {
        Diagnostics diags = new Diagnostics();
        SoundCue cue = SoundCue.from(yaml("{ sound: BLOCK_BEACON_POWER_SELECT, volume: 0.4, pitch: 2 }", diags), diags);
        assertEquals("BLOCK_BEACON_POWER_SELECT", cue.name());
        assertEquals(0.4f, cue.volume());
        assertEquals(2.0f, cue.pitch());
    }

    @Test
    void volumeAndPitchDefaultToOne() {
        Diagnostics diags = new Diagnostics();
        SoundCue cue = SoundCue.from(yaml("{ sound: ENTITY_GENERIC_EAT }", diags), diags);
        assertEquals(1.0f, cue.volume());
        assertEquals(1.0f, cue.pitch());
    }

    @Test
    void aMappingWithoutASoundNameIsNull() {
        Diagnostics diags = new Diagnostics();
        assertNull(SoundCue.from(yaml("{ volume: 1, pitch: 1 }", diags), diags));
    }

    @Test
    void aBadNumberFallsBackToOne() {
        Diagnostics diags = new Diagnostics();
        SoundCue cue = SoundCue.from(yaml("{ sound: X, volume: loud, pitch: high }", diags), diags);
        assertEquals(1.0f, cue.volume());
        assertEquals(1.0f, cue.pitch());
    }

    @Test
    void canonicalMapsEnumFormAndKeyFormToTheSameConstant() {
        // an enum-form token is already canonical
        assertEquals("BLOCK_BEACON_POWER_SELECT", SoundCue.canonical("BLOCK_BEACON_POWER_SELECT"));
        // a resource-key token (dots) maps to the same constant — dots become underscores, uppercased
        assertEquals("ENTITY_PLAYER_LEVELUP", SoundCue.canonical("entity.player.levelup"));
        assertEquals("BLOCK_BEACON_POWER_SELECT", SoundCue.canonical("block.beacon.power_select"));
        // a namespace prefix is stripped
        assertEquals("ENTITY_PLAYER_LEVELUP", SoundCue.canonical("minecraft:entity.player.levelup"));
    }

    @Test
    void listReadsEachMappingAndSkipsTheNameless() {
        Diagnostics diags = new Diagnostics();
        YamlNode parent = yaml("""
                sounds:
                  - { sound: A, volume: 1.0, pitch: 0.1 }
                  - { volume: 2 }
                  - { sound: B, pitch: 1.7 }
                """, diags);
        List<SoundCue> cues = SoundCue.list(parent, "sounds", diags);
        assertEquals(2, cues.size());
        assertEquals("A", cues.get(0).name());
        assertEquals(0.1f, cues.get(0).pitch());
        assertEquals("B", cues.get(1).name());
        assertEquals(1.7f, cues.get(1).pitch());
    }
}
