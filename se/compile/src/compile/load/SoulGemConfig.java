package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The configurable likeness + economy of the soul-gem item (docs/v3-directives.md §D), loaded from the
 * top-level {@code items/soul-gem.yml}. Immutable; lives in the {@link ItemsConfig} snapshot the runtime
 * reads and the {@code /se reload} transaction swaps. The soul subsystem renders the gem and runs its
 * economy from this — the gem is a DISTINCT configured item (material/name/lore), souls deposit on kills
 * ({@link #soulsPerKill}), and the messages narrate the soul-mode toggle + each spend.
 *
 * <p>Particles (active / on-activate / on-deactivate) and soul-colour tiers are intentionally not modelled
 * yet — they are cosmetic and arrive in a follow-up once the core item + economy ship.
 *
 * @param material         the gem's material token (resolved cross-version at use, e.g. {@code EMERALD})
 * @param name             the gem's display name (legacy {@code &} colour codes allowed)
 * @param lore             the gem's lore lines ({@code {AMOUNT}} / {@code {SOUL-COLOR}} placeholders allowed)
 * @param soulsPerKill     souls deposited into an active gem per kill (≥ 0)
 * @param messageActivate  chat message when soul mode is enabled ({@code null}/blank = silent)
 * @param messageDeactivate chat message when soul mode is disabled
 * @param messageSoulUse   chat message when souls are spent ({@code {AMOUNT}} = remaining)
 */
public record SoulGemConfig(
        String material,
        String name,
        List<String> lore,
        int soulsPerKill,
        String messageActivate,
        String messageDeactivate,
        String messageSoulUse) {

    public SoulGemConfig {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(lore);
        soulsPerKill = Math.max(0, soulsPerKill);
    }

    /** The built-in soul gem used when {@code items/soul-gem.yml} is absent or omits fields. */
    public static SoulGemConfig defaults() {
        return new SoulGemConfig(
                "EMERALD",
                "&aSoul Gem",
                List.of("&7Souls: &a{AMOUNT}", "&7Right-click to toggle soul mode."),
                1,
                "&aSoul mode &lON&a.",
                "&7Soul mode &lOFF&7.",
                "&7Souls remaining: &a{AMOUNT}");
    }
}
