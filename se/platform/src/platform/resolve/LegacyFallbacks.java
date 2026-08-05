package platform.resolve;

import java.util.Map;
import schema.spec.HandleCategory;

/**
 * Lossy 1.8-only DEGRADATIONS merged on top of the {@link Aliases} renames at resolve time: a token with no
 * 1.8 equivalent maps to the closest 1.8 constant so the effect still fires (visibly degraded) instead of
 * costing the whole publish — an unresolved handle is a BLOCKING {@code E_UNKNOWN_HANDLE}. Kept OUT of
 * {@link Aliases} because these are not renames: the migrator must never rewrite a modern config through them.
 *
 * <p>Every row exists because an ENGINE or CATALOGUE default names a post-1.8 constant, so no author can avoid
 * it: {@code BLOCK_ANVIL_PLACE} is the default catalogue's (1.8 spells that cue {@code random.anvil_land},
 * which its {@code ANVIL_LAND} constant already owns, so it cannot be an {@link Aliases} row without
 * duplicating that key) and {@code BLOCK_AMETHYST_BLOCK_CHIME} is {@code BLINK}'s arrival accent.
 *
 * <p>Data, not era API — it lives here rather than in the legacy overlay so the legacy handle-era gate can
 * resolve exactly as {@code LegacyHandleLookup} does instead of re-typing the table.
 */
public final class LegacyFallbacks {

    private static final Map<HandleCategory, Map<String, String>> BY_CATEGORY = Map.of(
            HandleCategory.PARTICLE, Map.of("SOUL", "SMOKE_LARGE"),
            HandleCategory.SOUND, Map.of(
                    "BLOCK_ANVIL_PLACE", "ANVIL_LAND",
                    "BLOCK_AMETHYST_BLOCK_CHIME", "NOTE_PLING"));

    private LegacyFallbacks() {
    }

    public static Map<String, String> forCategory(HandleCategory category) {
        return BY_CATEGORY.getOrDefault(category, Map.of());
    }
}
