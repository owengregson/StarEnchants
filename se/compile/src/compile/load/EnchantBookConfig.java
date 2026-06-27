package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The likeness shared by EVERY enchant book (§I), loaded from {@code items/enchant-book.yml}: no per-enchant
 * config — {@code {ENCHANT}}/{@code {LEVEL}}/{@code {SUCCESS}} are filled via placeholders.
 *
 * @param successLore   appended when the book carries an explicit {@code {SUCCESS}} chance
 * @param destroyOnFail whether a FAILED apply destroys the gear (a White Scroll spares it). Only a sub-100
 *                      roll (unopened/randomized book) can fail; a 100%-success book never reaches this.
 */
public record EnchantBookConfig(String material, String name, List<String> lore, List<String> successLore,
                                boolean destroyOnFail) {

    public EnchantBookConfig {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(lore);
        successLore = List.copyOf(successLore);
    }

    /** {@code destroyOnFail} defaults to {@code false} (safe with no config); the shipped yml enables it so the White Scroll has a purpose. */
    public static EnchantBookConfig defaults() {
        return new EnchantBookConfig(
                "ENCHANTED_BOOK",
                "{ENCHANT} &7Book",
                List.of("&7{DESCRIPTION}", "&7Drag onto a held/worn item to apply &fLevel {LEVEL}&7."),
                List.of("&7Success chance: &f{SUCCESS}%"),
                false);
    }
}
