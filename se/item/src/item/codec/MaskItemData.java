package item.codec;

import java.util.List;

/**
 * A physical mask item's on-item state (ADR-0074): the ordered list of child mask keys it carries — ONE for a
 * plain mask, MANY for a COMPOSITE. The Multi Crystal shape exactly (ADR-0034/0035), because the question is the
 * same one: N identities on ONE item, packed into ONE entry string.
 *
 * <p>Applied, the keys become the helmet's {@link CombatState#maskKey()} — {@code "masks/a+masks/b"} — so the
 * combat codec is UNTOUCHED and a helmet stamped before composites existed still decodes: a plain key has no
 * delimiter and reads back as a singleton. Every child's ability set then resolves as if that child were the
 * worn mask, the crystal component walk verbatim.
 *
 * <p>There is deliberately NO container def. "Multi-Mask" is a likeness — the {@code name-multi} template on
 * {@code items/mask.yml} — not a {@code content/masks/*.yml} file, exactly as "Multi Crystal" is. A def cannot
 * know which children an ITEM carries, so a def-declared child list could only ever describe one fixed
 * composite; per-item is the reading that matches how the item is actually built in play.
 *
 * <p>Order is load-bearing twice over: the FIRST child supplies the composite's head and colour (the worn
 * illusion shows one face, and it is the one the wearer merged onto), and the LAST is the topmost component an
 * extractor pops.
 *
 * @param keys the child mask keys (e.g. {@code masks/blaze}); 1..{@link #ABSOLUTE_MAX}, never empty
 */
public record MaskItemData(List<String> keys) {

    public static final String DELIMITER = KeyEntries.DELIMITER;

    /** Absolute PDC-bloat ceiling on children in one mask — a sanity guard above any sane {@code max-merge}. */
    public static final int ABSOLUTE_MAX = KeyEntries.ABSOLUTE_MAX;

    public MaskItemData {
        keys = KeyEntries.checked(keys, "mask item");
    }

    public static MaskItemData single(String key) {
        return new MaskItemData(List.of(key));
    }

    /** Whether this mask folds more than one child — the "Multi-Mask" likeness and the compound render. */
    public boolean isMulti() {
        return keys.size() > 1;
    }

    /** The child whose head the worn illusion shows and whose colour styles the compound name (owner ruling). */
    public String first() {
        return keys.get(0);
    }

    /** The helmet's {@code maskKey} encoding these children ({@code "a+b"}, or just {@code "a"} for a plain mask). */
    public String entry() {
        return KeyEntries.encode(keys);
    }

    /**
     * Fold {@code other} onto this mask, {@code other}'s children landing ON TOP (last, so an extractor pops the
     * most recently merged child first). Rejected — {@code null} — when the combined child count would exceed
     * {@code maxChildren} (the {@code masks.max-merge} cap) or {@link #ABSOLUTE_MAX}.
     */
    public MaskItemData mergeWith(MaskItemData other, int maxChildren) {
        if (other == null) {
            return null;
        }
        List<String> combined = KeyEntries.merged(keys, other.keys, maxChildren);
        return combined == null ? null : new MaskItemData(combined);
    }

    /** Split a helmet's {@code maskKey} (or a mask item's stamp) into its child keys (a plain key → singleton). */
    public static List<String> componentsOf(String entry) {
        return KeyEntries.componentsOf(entry);
    }
}
