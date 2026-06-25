package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The general likeness shared by EVERY enchant book (docs/v3-directives.md §I), loaded from the top-level
 * {@code items/enchant-book.yml}: there is no per-enchant book config — enchant/level/success are filled
 * in via placeholders. Immutable; lives in the {@link ItemsConfig} snapshot {@code /se reload} swaps.
 *
 * <p>Placeholders: {@code {ENCHANT}} = display name, {@code {LEVEL}} = granted level, {@code {SUCCESS}} =
 * apply success chance (only meaningful in {@link #successLore}, appended when the book overrides success).
 *
 * @param material      the book item's material token (resolved cross-version; default ENCHANTED_BOOK)
 * @param successLore   extra lore appended when the book carries an explicit success chance ({@code {SUCCESS}})
 * @param destroyOnFail whether a FAILED book apply destroys the gear (a White Scroll spares it). Only a
 *                      sub-100 roll (unopened/randomized book) can fail; a 100%-success book never reaches this.
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
     * Built-in likeness used when {@code items/enchant-book.yml} is absent. {@code destroyOnFail} defaults
     * to {@code false} (safe with no config); the shipped yml enables it so the White Scroll has a purpose.
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
