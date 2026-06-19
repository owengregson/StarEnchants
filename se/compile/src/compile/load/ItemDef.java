package compile.load;

import java.util.List;
import schema.diag.Source;

/**
 * The parsed metadata of one authored identity/carrier item (ADR-0016 §4) — a book / scroll / dust /
 * gem that <em>applies</em> an enchant/crystal/set to other gear (or modifies an application). It is
 * pure PDC + presentation metadata: it expands to ZERO {@code AbilityDef}s, never reaches the compiler,
 * and never touches the combat hot path. It is the forward-compat home for the carrier-application
 * feature (ADR-0016, item-data-model) and is inert until that feature ships. Immutable.
 *
 * @param key         the path-derived key (e.g. {@code items/book/thunderstrike-book})
 * @param display     the display name (colour codes intact)
 * @param description the description / lore; never {@code null} (empty if absent)
 * @param tier        the rarity tier (may be {@code null})
 * @param kind        the carrier archetype: {@code book|tome|scroll|dust|gem}
 * @param material    the vanilla item material token it renders as (resolved at use, never here)
 * @param glow        whether the item carries an enchant glint
 * @param grant       what it confers (nullable — a pure-mechanic carrier like a protect scroll has none)
 * @param apply       the application mechanics (success/destroy/protect)
 * @param source      where it was authored
 */
public record ItemDef(
        String key,
        String display,
        String description,
        String tier,
        String kind,
        String material,
        boolean glow,
        Grant grant,
        Apply apply,
        Source source) {

    /**
     * What a carrier confers. Exactly one of {@code enchant}/{@code crystal}/{@code set} for a book-like
     * carrier (with {@code level} for an enchant); {@code successBonus} for a dust; {@code role} for a
     * scroll. {@code sound}/{@code particles} are the §I apply-feedback (used by dust today): a namespaced
     * sound token and particle tokens played/spawned on a successful apply. Unused fields are
     * {@code null}/{@code 0}/empty.
     */
    public record Grant(String enchant, String crystal, String set, int level, Integer successBonus, String role,
                        String sound, List<String> particles) {

        public Grant {
            particles = particles == null ? List.of() : List.copyOf(particles);
        }

        /** A grant with no apply-feedback (the common form for non-dust carriers). */
        public Grant(String enchant, String crystal, String set, int level, Integer successBonus, String role) {
            this(enchant, crystal, set, level, successBonus, role, null, List.of());
        }
    }

    /** The application mechanics for a carrier: success chance, gear-destroy-on-fail, and protectability. */
    public record Apply(int successChance, boolean destroyOnFail, boolean protectable, List<String> appliesTo) {

        public Apply {
            appliesTo = List.copyOf(appliesTo);
        }
    }
}
