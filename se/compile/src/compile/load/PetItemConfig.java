package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The UNIVERSAL pet likeness (ADR-0052), loaded from {@code items/pet.yml} ({@code type: pet}) — one
 * name/lore template every pet head is rendered from (the enchant-book precedent; there is no per-pet item
 * file). ACTIVE and PASSIVE pets carry different fixed lines (the cooldown block, the usage hint), so each
 * type owns a whole lore template rather than splicing conditionals into one.
 *
 * <p>Tokens (name + lore): {@code {COLOR}} the pet's colour codes, {@code {NAME}} its display name,
 * {@code {DESCRIPTION}} the per-pet description (one lore line per authored line), {@code {TIME_FORMATTED}}
 * the live bracket's cooldown (h/m/s). {@code levelLine} renders once below the lore with {@code {LEVEL}} /
 * {@code {MAX_LEVEL}} / {@code {EXP}} / {@code {EXP_NEXT}}; blank = no level line.
 */
public record PetItemConfig(String name, List<String> loreActive, List<String> lorePassive, String levelLine) {

    public PetItemConfig {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(levelLine, "levelLine");
        loreActive = List.copyOf(loreActive);
        lorePassive = List.copyOf(lorePassive);
    }

    public static PetItemConfig defaults() {
        return new PetItemConfig(
                "{COLOR}&l{NAME} Pet",
                List.of("&lACTIVE&r {COLOR}&lABILITY",
                        "{DESCRIPTION}",
                        "",
                        "&eCooldown: &r&f&n{TIME_FORMATTED}",
                        "&7Right Click while holding to apply."),
                List.of("&e&lPASSIVE&r {COLOR}&lABILITY",
                        "{DESCRIPTION}",
                        "",
                        "&7Hold in hotbar to apply."),
                "&eLevel: &a&n{LEVEL}&r&e/&a{MAX_LEVEL}");
    }
}
