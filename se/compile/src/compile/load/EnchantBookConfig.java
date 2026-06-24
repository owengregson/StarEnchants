package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The GENERAL likeness of an enchant BOOK (docs/v3-directives.md §I), loaded from the top-level
 * {@code items/enchant-book.yml}. There is no per-enchant book config: EVERY enchant book — minted by
 * {@code /se give book}, the unopened/randomized book, or a drop — shares this one likeness, with the
 * enchant/level/success filled in via placeholders. Immutable; lives in the {@link ItemsConfig} snapshot
 * the runtime reads and {@code /se reload} swaps.
 *
 * <p>Placeholders: {@code {ENCHANT}} = the enchant's display name, {@code {LEVEL}} = the granted level,
 * {@code {SUCCESS}} = the apply success chance (only meaningful in {@link #successLore}, appended when the
 * book carries an explicit success override).
 *
 * @param material      the book item's material token (resolved cross-version; default ENCHANTED_BOOK)
 * @param name          the display name ({@code &} colours; {@code {ENCHANT}})
 * @param lore          the base lore lines ({@code &} colours; {@code {LEVEL}})
 * @param successLore   extra lore lines appended when the book has an explicit success chance ({@code {SUCCESS}})
 * @param destroyOnFail whether a FAILED book apply destroys the gear (unless a White Scroll guard spares it).
 *                      A book that always succeeds (success 100) never reaches this; only an unopened/randomized
 *                      book's sub-100 roll can fail. The White Scroll ({@code items/white-scroll.yml}) protects it.
 */
public record EnchantBookConfig(String material, String name, List<String> lore, List<String> successLore,
                                boolean destroyOnFail) {

    public EnchantBookConfig {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(lore);
        successLore = List.copyOf(successLore);
    }

    /**
     * The built-in enchant-book likeness used when {@code items/enchant-book.yml} is absent or omits fields.
     * {@code destroyOnFail} defaults to {@code false} (the safe fallback with no config); the shipped
     * {@code items/enchant-book.yml} enables it so the White Scroll has a purpose out of the box.
     */
    public static EnchantBookConfig defaults() {
        return new EnchantBookConfig(
                "ENCHANTED_BOOK",
                "{ENCHANT} &7Book",
                List.of("&7Drag onto a held/worn item to apply &fLevel {LEVEL}&7."),
                List.of("&7Success chance: &f{SUCCESS}%"),
                false);
    }
}
