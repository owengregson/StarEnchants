package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The likeness shared by EVERY enchant book (§I), loaded from {@code items/enchant-book.yml}: no per-enchant
 * config — placeholders are filled per book. Recognised placeholders in {@code name}/{@code lore}:
 * {@code {ENCHANT}} (display name), {@code {LEVEL}} (granted level, Roman/Arabic per {@code lore.roman}),
 * {@code {TIER_COLOR}} (the enchant's rarity-tier colour code), {@code {SUCCESS}} / {@code {FAILURE}} (the
 * apply success / 100-minus-success), {@code {KINDS}} (the grammatically-joined applies-to kinds), and
 * {@code {DESCRIPTION}} (the enchant's description, word-wrapped to {@code wrap} visible chars — each wrapped
 * line becomes its own lore entry).
 *
 * @param successLore   appended only when the book carries an explicit {@code {SUCCESS}} chance; normally
 *                      empty because the success/failure rate is shown in the main {@code lore}
 * @param destroyOnFail whether a FAILED apply destroys the gear (a White Scroll spares it). Only a sub-100
 *                      roll (unopened/randomized book) can fail; a 100%-success book never reaches this.
 * @param wrap          word-wrap width (visible chars) for {@code {DESCRIPTION}}; {@code <= 0} disables wrapping
 */
public record EnchantBookConfig(String material, String name, List<String> lore, List<String> successLore,
                                boolean destroyOnFail, int wrap) {

    public EnchantBookConfig {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(lore);
        successLore = List.copyOf(successLore);
    }

    /**
     * The built-in spec: a tier-coloured bold name, a blank line, the word-wrapped description, the success
     * and failure rates, a blank line, the applies-to kinds, and the drag-and-drop footer. {@code destroyOnFail}
     * defaults to {@code false} (safe with no config); the shipped yml enables it so the White Scroll has a purpose.
     */
    public static EnchantBookConfig defaults() {
        return new EnchantBookConfig(
                "ENCHANTED_BOOK",
                "{TIER_COLOR}&l{ENCHANT} {LEVEL}",
                List.of(
                        "",
                        "{TIER_COLOR}{DESCRIPTION}",
                        "&a{SUCCESS}% Success Rate",
                        "&c{FAILURE}% Failure Rate",
                        "",
                        "&7{KINDS} Enchantment",
                        "&7Drag n' Drop on an item to apply."),
                List.of(),
                false,
                30);
    }
}
