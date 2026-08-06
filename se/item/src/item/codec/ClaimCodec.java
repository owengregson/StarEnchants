package item.codec;

import org.bukkit.inventory.ItemStack;

/**
 * The set-piece CLAIM footer's state (R-QC35c): who staked the piece, and when. Two PDC strings on the
 * {@link HolyProtectionCodec} / {@link TrakCodec} precedent — kept OUT of the {@link CombatState} blob because
 * a claim is written by an external event system on its own schedule, and folding it into the content-hash
 * would thrash the {@code ItemView} cache every time one lands.
 *
 * <p>The DATE is stored already formatted rather than as an instant. The claim is not this plugin's event:
 * the format it is written in (the recorded {@code EEE MM/dd/yy}) belongs to whoever runs the event, and
 * re-formatting an epoch here would need a locale and a zone contract the port has no source for. A piece with
 * no date carries no claim at all and renders no footer; a piece with a date and no claimant renders the
 * unclaimed form, which is exactly the pair the recorded strings describe.
 */
public final class ClaimCodec {

    private final String claimantKey;
    private final String dateKey;
    private final ItemStateStore store;

    public ClaimCodec(String claimantKey, String dateKey, ItemStateStore store) {
        this.claimantKey = claimantKey;
        this.dateKey = dateKey;
        this.store = store;
    }

    /** Who claimed {@code stack}, or {@code null} on an unclaimed (or unstaked) piece. */
    public String claimant(ItemStack stack) {
        return blankToNull(store.read(stack, claimantKey));
    }

    /** The claim's rendered date, or {@code null} when the piece carries no claim at all. */
    public String date(ItemStack stack) {
        return blankToNull(store.read(stack, dateKey));
    }

    /**
     * Stamp a claim. A {@code null} claimant is the UNCLAIMED state — the piece is staked (it has a date) but
     * nobody holds it — which is a real state the footer renders, not an absence.
     */
    public void write(ItemStack stack, String claimant, String date) {
        store.write(stack, claimantKey, claimant == null ? "" : claimant);
        store.write(stack, dateKey, date == null ? "" : date);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
