package bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.load.MasterConfig;
import compile.load.MasterConfigLoader;
import compile.load.SoundCue;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import platform.resolve.Aliases;
import platform.resolve.HandleResolver;
import platform.resolve.LegacyFallbacks;
import schema.spec.HandleCategory;

/**
 * The era gate the handle-era tests cannot reach: {@code ModernHandleEraTest} / {@code LegacyHandleEraTest}
 * compile {@code content/} only, so a master-config sound cue never passes through {@code HandleResolver} at
 * build time and an unresolvable one produces no diagnostic — {@code feature.compat.Sounds} just plays nothing.
 * Every shipped {@code config.yml} cue must therefore exist at the declared 1.17.1 floor, and a legacy-capable
 * pack must keep at least one audible layer per chord on 1.8.8.
 */
class ConfigCueEraTest {

    private static final String FLOOR_FIXTURE = "test-fixtures/handles/sounds-1.18.2.txt";
    private static final String LEGACY_FIXTURE = "test-fixtures/handles/sounds-1.8.8.txt";

    @ParameterizedTest
    @ValueSource(strings = {"resources/config.yml",
                            "packs-src/cosmic-pack/config.yml",
                            "packs-src/signature-pack/config.yml"})
    void everyConfigSoundCueExistsAtTheModernFloor(String configPath) {
        Set<String> floor = constants(FLOOR_FIXTURE);
        Map<String, String> table = Aliases.mergedWith(
                HandleCategory.SOUND, LegacyFallbacks.forCategory(HandleCategory.SOUND));
        String unresolvable = cueTokens(configPath).stream()
                .filter(token -> !resolves(token, floor, table))
                .collect(Collectors.joining(", "));
        assertTrue(unresolvable.isEmpty(),
                () -> configPath + " names sound constants that do not exist at the 1.17.1 floor: " + unresolvable
                        + " — the cue is skipped in silence there, with no diagnostic");
    }

    /**
     * cosmic-pack declares no {@code min-server}, so it loads on the 1.8.9 lane. A chord whose every layer is
     * modern-only is a feedback beat that silently disappears on that lane, which is what the pack's own header
     * promises it is not.
     */
    @Test
    void everyCosmicPackChordKeepsAnAudibleLayerOnLegacy() {
        Set<String> legacy = constants(LEGACY_FIXTURE);
        Map<String, String> table = Aliases.mergedWith(
                HandleCategory.SOUND, LegacyFallbacks.forCategory(HandleCategory.SOUND));
        MasterConfig config = load("packs-src/cosmic-pack/config.yml");
        Map<String, List<String>> chords = Map.of(
                "sets.equip-sound", names(config.sets().equipSound()),
                "sets.unequip-sound", names(config.sets().unequipSound()),
                "apply-cues.success", List.of(config.applyCues().successSound().name()),
                "apply-cues.fail", List.of(config.applyCues().failSound().name()),
                "pets.level-up-sound", List.of(config.pets().levelUpSound().name()));

        String mute = chords.entrySet().stream()
                .filter(chord -> !chord.getValue().isEmpty())
                .filter(chord -> chord.getValue().stream().noneMatch(token -> resolves(token, legacy, table)))
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(", "));
        assertTrue(mute.isEmpty(),
                () -> "cosmic-pack config cues with no layer that resolves on 1.8.8: " + mute);
    }

    private static boolean resolves(String token, Set<String> constants, Map<String, String> table) {
        return HandleResolver.resolve(SoundCue.canonical(token), table, constants::contains).isPresent();
    }

    /** Every sound token a master config carries: both set chords, both apply cues, the pet level-up cue. */
    private static List<String> cueTokens(String configPath) {
        MasterConfig config = load(configPath);
        List<String> tokens = new ArrayList<>();
        tokens.addAll(names(config.sets().equipSound()));
        tokens.addAll(names(config.sets().unequipSound()));
        tokens.add(config.applyCues().successSound().name());
        tokens.add(config.applyCues().failSound().name());
        tokens.add(config.pets().levelUpSound().name());
        tokens.removeIf(token -> token == null || token.isBlank());
        assertFalse(tokens.isEmpty(), () -> configPath + " parsed with no sound cues at all");
        return tokens;
    }

    private static List<String> names(List<SoundCue> cues) {
        return cues.stream().map(SoundCue::name).toList();
    }

    private static MasterConfig load(String configPath) {
        Path path = Path.of(configPath);
        assertTrue(Files.isRegularFile(path), () -> path + " not found from " + Path.of("").toAbsolutePath());
        return MasterConfigLoader.load(path);
    }

    private static Set<String> constants(String fixture) {
        Path path = Path.of(fixture);
        try {
            Set<String> names = Files.readAllLines(path).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toUnmodifiableSet());
            assertTrue(names.size() > 50, "suspiciously small constant list: " + path);
            return names;
        } catch (IOException e) {
            throw new UncheckedIOException("missing committed era constants: " + path, e);
        }
    }
}
