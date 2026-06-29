package item.codec;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.bukkit.inventory.ItemStack;

/**
 * The set of APPLIED-UTILITY markers on a piece of gear (§I). Despite the singular historical name — and the PDC
 * key, kept as {@code "appliedslot"} for data compatibility — this is a SET: an item may carry ANY combination
 * of a white scroll guard, a holy white scroll keep-marker, and the trak gems at once (e.g. MobTrak + SoulTrak
 * on one weapon, or a trak gem alongside a white scroll). The markers are stored comma-joined as a PDC
 * {@code STRING} under {@link ItemKeys#appliedSlot()}, off the combat hot path; a legacy single-value entry
 * (the former one-occupant model) reads back as a one-element set, so no migration is needed.
 *
 * <p>Each applier {@link #occupy}s its own marker on success and {@link #release}s it when that marker is
 * consumed (the white scroll on a failed enchant save, the holy scroll on death). The trak gems never release —
 * once applied they ride the item for its lifetime. Markers are independent: applying one neither blocks nor
 * clears another.
 */
public final class AppliedSlot {

    public static final String WHITE_SCROLL = "white-scroll";
    public static final String HOLY = "holy";
    public static final String BLOCKTRAK = "blocktrak";
    public static final String MOBTRAK = "mobtrak";
    public static final String SOULTRAK = "soultrak";
    public static final String FISHTRAK = "fishtrak";

    private static final String DELIMITER = ",";

    private final String key;

    public AppliedSlot(String key) {
        this.key = key;
    }

    /** The markers currently on {@code stack} (insertion-ordered; empty if none). */
    public Set<String> markers(ItemStack stack) {
        return parse(ItemBlobStore.read(stack, key));
    }

    /**
     * Parse a stored marker blob into its set (insertion-ordered, blanks/duplicates dropped). A legacy
     * single-value entry (no delimiter, the former one-occupant model) parses to a one-element set, so old
     * items keep their applied marker with no migration.
     */
    public static Set<String> parse(String raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw != null && !raw.isBlank()) {
            for (String part : raw.split(DELIMITER)) {
                if (!part.isBlank()) {
                    out.add(part);
                }
            }
        }
        return out;
    }

    /** Serialize a marker set to its stored blob (comma-joined; empty for none). */
    public static String serialize(Collection<String> markers) {
        return String.join(DELIMITER, markers);
    }

    /** Whether {@code stack} already carries {@code kind} (a no-op re-apply guard). */
    public boolean holds(ItemStack stack, String kind) {
        return markers(stack).contains(kind);
    }

    /**
     * Whether {@code kind} may be applied to {@code stack}: yes, unless it is already present. Markers are
     * independent, so a different marker never blocks one — this is purely the per-kind re-apply guard.
     */
    public boolean canApply(ItemStack stack, String kind) {
        return !holds(stack, kind);
    }

    /** Add {@code kind} to the marker set (idempotent). */
    public void occupy(ItemStack stack, String kind) {
        Set<String> markers = markers(stack);
        if (markers.add(kind)) {
            write(stack, markers);
        }
    }

    /** Remove {@code kind} from the marker set, clearing the key entirely when the last marker goes. */
    public void release(ItemStack stack, String kind) {
        Set<String> markers = markers(stack);
        if (markers.remove(kind)) {
            write(stack, markers);
        }
    }

    private void write(ItemStack stack, Set<String> markers) {
        ItemBlobStore.write(stack, key, markers.isEmpty() ? null : serialize(markers));
    }
}
