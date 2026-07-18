package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The UNIVERSAL weapon-reforge likeness (ADR-0070), loaded from {@code items/reforge.yml}
 * ({@code type: reforge}) — one name/lore template every reforge item is rendered from (the mask.yml
 * precedent; there is no per-reforge item file). Unlike a mask, the ITEM MATERIAL is per-reforge (the
 * content {@code icon:} — cosmetic catalogue identity), so this likeness carries no material.
 *
 * <p>Tokens (name + lore): {@code {COLOR}} the reforge's colour codes, {@code {NAME}} its display name,
 * {@code {NAME_UPPER}} upper-cased, {@code {DESCRIPTION}} the per-reforge description (line-expanding),
 * {@code {APPLIES}} the configured weapon-groups label, {@code {TIME_FORMATTED}} the active's cooldown.
 * {@code loreWhileOnItem} is the single line shown on a reforged WEAPON ({@code {NAME}} = the reforge's
 * colour-styled display).
 *
 * @param name            the reforge item's display name template
 * @param lore            the reforge item's lore template (line-expanding {@code {DESCRIPTION}})
 * @param loreWhileOnItem the single line shown on a reforged weapon (VERBATIM owner spec — do not restyle)
 * @param sounds          whether the apply/remove cues play (the crystal {@code sounds.enabled} shape)
 * @param soundApply      cue played when a reforge is applied to a weapon
 * @param soundRemove     cue played when the Item Extractor pops a reforge back off
 */
public record ReforgeItemConfig(String name, List<String> lore, String loreWhileOnItem, boolean sounds,
                                SoundCue soundApply, SoundCue soundRemove) {

    public ReforgeItemConfig {
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(lore);
        Objects.requireNonNull(loreWhileOnItem, "loreWhileOnItem");
        Objects.requireNonNull(soundApply, "soundApply");
        Objects.requireNonNull(soundRemove, "soundRemove");
    }

    public static ReforgeItemConfig defaults() {
        return new ReforgeItemConfig(
                "&6&lWeapon Reforge ({NAME}&6&l)",
                List.of(),
                "&6&lWeapon Reforge (&r{NAME}&6&l)",
                true,
                new SoundCue("block.anvil.use", 1.0f, 1.0f),
                new SoundCue("block.anvil.land", 1.0f, 1.0f));
    }
}
