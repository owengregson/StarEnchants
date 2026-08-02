package item.render;

import platform.text.Colors;
import platform.text.Tokens;

/**
 * The holy-scroll CORRUPTION lore line, rendered from an item's spent-protection count (never parsed back, §4.2).
 * A holy white scroll may only save one item so many times: each protection actually DELIVERED (the keep-marker
 * consumed by a death) bumps the count, and the item advertises how far through its allowance it is. At the
 * maximum no further holy scroll may be applied.
 *
 * <p>The stage is a percentage of the allowance, not an absolute count, so it reads the same at any configured
 * maximum: {@code 1–49%} SEMI, {@code 50–99%} VERY, {@code 100%} FULL. The caller appends this directly below the
 * holy PROTECTED line ({@link ProtectionLore}) and re-renders whenever the count changes — so unlike the
 * protection line, which vanishes with its marker, this one is permanent once earned.
 */
public final class CorruptionLore {

    /** How far through its holy-protection allowance an item is. */
    public enum Stage {
        /** No protections spent yet, or corruption disabled ({@code max <= 0}) — no line. */
        NONE,
        /** 1–49% of the allowance spent. */
        SEMI,
        /** 50–99% spent. */
        VERY,
        /** The allowance is exhausted; no further holy white scroll may be applied. */
        FULL
    }

    private CorruptionLore() {
    }

    /**
     * The stage for {@code count} protections spent out of {@code max}. A {@code max <= 0} disables corruption
     * entirely ({@link Stage#NONE} at any count, so no line renders and no apply is ever refused); a count at or
     * beyond {@code max} is {@link Stage#FULL}, so a lowered maximum retroactively corrupts rather than
     * un-corrupting an item that has already outrun it.
     */
    public static Stage stageOf(int count, int max) {
        if (max <= 0 || count <= 0) {
            return Stage.NONE;
        }
        if (count >= max) {
            return Stage.FULL;
        }
        return 100 * count / max >= 50 ? Stage.VERY : Stage.SEMI;
    }

    /**
     * The rendered corruption line for {@code count}/{@code max}, or {@code null} at {@link Stage#NONE} (nothing
     * to show). {@code {AMOUNT}} and {@code {MAX}} expand in the stage's template, which is then colour-translated.
     */
    public static String line(int count, int max, String semiTemplate, String veryTemplate, String fullTemplate) {
        String template = switch (stageOf(count, max)) {
            case NONE -> null;
            case SEMI -> semiTemplate;
            case VERY -> veryTemplate;
            case FULL -> fullTemplate;
        };
        if (template == null || template.isEmpty()) {
            return null; // an operator may blank one stage's template to hide that stage's line
        }
        return Colors.translate(Tokens.sub(template, "AMOUNT", count, "MAX", max));
    }
}
