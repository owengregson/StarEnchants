package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The UNIVERSAL pet likeness (ADR-0052), loaded from {@code items/pet.yml} ({@code type: pet}) — one
 * name/lore template every pet head is rendered from (the enchant-book precedent; there is no per-pet item
 * file). ACTIVE and PASSIVE pets carry different fixed lines (the tag, the cooldown block, the usage hint),
 * so each type owns a whole lore template rather than splicing conditionals into one.
 *
 * <p>Tokens (name + lore): {@code {COLOR}} the pet's colour codes ({@code &{COLOR}} is accepted too),
 * {@code {NAME}} its display name, {@code {DESCRIPTOR}} the pet's flavour line(s) (one lore line per
 * authored line), {@code {DESCRIPTION}} the per-pet ability description (one lore line per authored line),
 * {@code {TIME_FORMATTED}} the live bracket's cooldown (h/m/s), {@code {LEVEL}} / {@code {MAX_LEVEL}} /
 * {@code {EXP}} / {@code {EXP_NEXT}} the stored progress against the universal knobs, and {@code {EXP_BAR}}
 * a ten-slot progress meter ({@code &a▪} filled / {@code &7_} empty) toward the next level — full at the
 * level cap.
 */
public record PetItemConfig(String name, List<String> loreActive, List<String> lorePassive) {

    public PetItemConfig {
        Objects.requireNonNull(name, "name");
        loreActive = List.copyOf(loreActive);
        lorePassive = List.copyOf(lorePassive);
    }

    public static PetItemConfig defaults() {
        return new PetItemConfig(
                "{COLOR}&l{NAME} Pet",
                List.of("{DESCRIPTOR}",
                        "",
                        "{COLOR}&lACTIVE PET ABILITY",
                        "{DESCRIPTION}",
                        "",
                        "&eLevel: &r&f&n{LEVEL}&r&7 / {MAX_LEVEL}",
                        "&eExperience: &r&f[{EXP_BAR}&f]",
                        "",
                        "&eCooldown: &r&f&n{TIME_FORMATTED}",
                        "&7Right Click while holding to apply."),
                List.of("{DESCRIPTOR}",
                        "",
                        "{COLOR}&lPASSIVE PET ABILITY",
                        "{DESCRIPTION}",
                        "",
                        "&eLevel: &r&f&n{LEVEL}&r&7 / {MAX_LEVEL}",
                        "&eExperience: &r&f[{EXP_BAR}&f]",
                        "",
                        "&7Hold in hotbar to apply."));
    }
}
