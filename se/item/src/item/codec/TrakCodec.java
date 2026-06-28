package item.codec;

import java.util.Locale;
import org.bukkit.inventory.ItemStack;

/**
 * Trak-gem item state (§I): the unapplied-gem marker (which of the three kinds a gem is) and the three per-item
 * lifetime counters (blocks broken / mobs killed / players killed). The counters are tracked in the BACKGROUND
 * on every eligible tool/weapon — long before any gem is applied — so an applied gem can reveal a true
 * lifetime count. Each counter is its own PDC {@code INTEGER}, separate from the {@link CombatState} blob, so a
 * per-event bump never thrashes the content-hash {@link ItemView} cache (cf. {@link SoulData}).
 */
public final class TrakCodec {

    /** The three trackable lifetime statistics. */
    public enum Kind { BLOCK, MOB, SOUL }

    private final String gemKey;
    private final String blocksKey;
    private final String mobsKey;
    private final String soulsKey;

    public TrakCodec(String gemKey, String blocksKey, String mobsKey, String soulsKey) {
        this.gemKey = gemKey;
        this.blocksKey = blocksKey;
        this.mobsKey = mobsKey;
        this.soulsKey = soulsKey;
    }

    /** Stamp {@code stack} as an unapplied trak gem of {@code kind}. */
    public void markGem(ItemStack stack, Kind kind) {
        ItemBlobStore.write(stack, gemKey, kind.name());
    }

    /** The trak gem kind {@code stack} is, or {@code null} if it is not a trak gem. */
    public Kind gemKind(ItemStack stack) {
        String raw = ItemBlobStore.read(stack, gemKey);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Kind.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null; // a malformed marker is treated as not-a-gem, never crashes
        }
    }

    /** The lifetime count of {@code kind} on {@code stack} (0 if none). */
    public int count(ItemStack stack, Kind kind) {
        return Math.max(0, ItemFlagStore.readInt(stack, counterKey(kind), 0));
    }

    /** Bump the lifetime count of {@code kind} on {@code stack} by one and return the new value. */
    public int increment(ItemStack stack, Kind kind) {
        int next = count(stack, kind) + 1;
        ItemFlagStore.writeInt(stack, counterKey(kind), next);
        return next;
    }

    private String counterKey(Kind kind) {
        return switch (kind) {
            case BLOCK -> blocksKey;
            case MOB -> mobsKey;
            case SOUL -> soulsKey;
        };
    }
}
