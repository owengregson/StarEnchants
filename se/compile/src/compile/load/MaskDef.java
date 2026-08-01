package compile.load;

import java.util.List;
import schema.diag.Source;

/**
 * Non-runtime metadata of one authored mask (ADR-0014, ADR-0053) — a textured player-head item that
 * drag-applies onto a HELMET (exclusively). A mask has no levels and no {@code appliesTo}: it is helmets-only
 * by construction (the service validates against the {@code ItemGroups} HELMET group), so the def carries no
 * target groups. It expands to one or more {@code AbilityDef}s ({@code <key>}, then {@code <key>/a1}, {@code /a2},
 * … like a crystal's bonuses) so one mask can carry independent effects across triggers.
 *
 * <p>The likeness (name/lore) is NOT per-mask — it is the ONE global {@link MaskItemConfig}
 * ({@code items/mask.yml}). A mask file declares only its identity: the bare {@code display} name filled into the
 * universal likeness as {@code {NAME}}, the {@code color} code sequence filled as {@code {COLOR}}, the
 * {@code description} block rendered into {@code {DESCRIPTION}}, and the {@code head} base64 texture the worn
 * repaint shows (with a {@code material} fallback when the head is untexturable).
 *
 * @param key         the source-prefixed key ({@code masks/<stem>}) a masked helmet stamps and {@link Library#maskDefOf} looks up
 * @param display     bare display name (no colour codes) filled into the universal likeness as {@code {NAME}}
 * @param color       the mask's colour code sequence (e.g. {@code "&6"}), filled as {@code {COLOR}}
 * @param description the authored description lines, verbatim (filled into {@code {DESCRIPTION}})
 * @param summary     short special-ability label rendered for this component inside a Multi-Mask
 * @param head        base64 texture value for the worn player-head repaint; {@code ""} = untextured (material fallback)
 * @param material    material token shown when head textures are unsupported; defaults to {@code PLAYER_HEAD}
 */
public record MaskDef(
        String key,
        String display,
        String color,
        List<String> description,
        String summary,
        String head,
        String material,
        Source source) {

    public MaskDef {
        description = List.copyOf(description);
    }
}
