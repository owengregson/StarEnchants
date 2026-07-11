package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The UNIVERSAL mask likeness (ADR-0053), loaded from {@code items/mask.yml} ({@code type: mask}) — one
 * name/lore template every mask item is rendered from (the pet.yml precedent; there is no per-mask item file).
 *
 * <p>Tokens (name + lore): {@code {COLOR}} the mask's colour codes, {@code {NAME}} its display name,
 * {@code {DESCRIPTION}} the per-mask description (one lore line per authored line), {@code {APPLIES}} the
 * {@code ItemGroups} kinds label for HELMET (masks are helmets-only). {@code loreWhileOnItem} is the single
 * line shown on a masked HELMET, rendered from the same {@code {NAME}} token (colour-styled). Apply/remove use
 * the same shared {@code sounds} cues the crystal likeness carries.
 *
 * @param name            the mask item's display name template
 * @param lore            the mask item's lore template (line-expanding {@code {DESCRIPTION}})
 * @param loreWhileOnItem the single line shown on a masked helmet
 * @param sounds          whether the apply/remove cues play (the crystal {@code sounds.enabled} shape)
 * @param soundApply      cue played when a mask is applied to a helmet
 * @param soundRemove     cue played when a mask is popped back off
 */
public record MaskItemConfig(String name, List<String> lore, String loreWhileOnItem, boolean sounds,
                             SoundCue soundApply, SoundCue soundRemove) {

    public MaskItemConfig {
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(lore);
        Objects.requireNonNull(loreWhileOnItem, "loreWhileOnItem");
        Objects.requireNonNull(soundApply, "soundApply");
        Objects.requireNonNull(soundRemove, "soundRemove");
    }

    public static MaskItemConfig defaults() {
        return new MaskItemConfig(
                "{COLOR}&l{NAME} Mask",
                List.of(),
                "&6&lMask Equipped ({NAME}&r&6&l)",
                true,
                new SoundCue("block.amethyst_block.chime", 1.0f, 1.0f),
                new SoundCue("block.amethyst_cluster.break", 1.0f, 1.0f));
    }
}
