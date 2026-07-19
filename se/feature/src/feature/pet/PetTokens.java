package feature.pet;

import java.util.ArrayList;
import java.util.List;

/**
 * Template tolerance for the pet {@code {COLOR}} token (ADR-0052): the pack spec writes {@code &{COLOR}}
 * (the colour as a bare code) while the substituted value carries its own ampersands ({@code "&7"}), which
 * would render a literal {@code &} ({@code &&7} — the translator only converts {@code &} before a valid
 * code). Both authored forms mean the same thing, so {@code &{COLOR}} folds to {@code {COLOR}} before
 * substitution — never a per-render policy an operator has to know about.
 */
final class PetTokens {

    private PetTokens() {
    }

    static String colorTolerant(String template) {
        return platform.text.Tokens.colorTolerant(template); // the shared fold (promoted for reforges)
    }

    static List<String> colorTolerant(List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(colorTolerant(line));
        }
        return out;
    }
}
