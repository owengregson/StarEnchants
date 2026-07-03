package feature.menu;

import compile.load.ContentHolder;
import compile.load.Library;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.inventory.ItemStack;

/**
 * The operator {@link MintMenu}'s tiles, DERIVED from the module-declared {@link Mintable}s (ADR-0047): the ONE
 * mint declaration set behind the menu, {@code /se give} and the self-mint rows. Entries stay suppliers (not
 * pre-built stacks) so every open mints against the live config, and a gated subsystem whose mint throws is
 * skipped at render rather than breaking the menu. The tiles are the {@code tileRank}-sorted expansion of each
 * mintable's {@link Mintable#tiles}, so the shipped curated order (soul gem → traks → one book per tier) holds.
 */
public final class MintCatalog {

    /** One mintable item: the label is for the give-confirmation message; the thunk mints a fresh stack. */
    public record Entry(String label, Supplier<ItemStack> mint) {
        public Entry {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(mint, "mint");
        }
    }

    private final List<Mintable> mintables;
    private final ContentHolder content;

    public MintCatalog(List<Mintable> mintables, ContentHolder content) {
        this.mintables = List.copyOf(mintables);
        this.content = Objects.requireNonNull(content, "content");
    }

    /** Every mintable item, in the shipped curated order: stable-sort by tile rank, expand tiles live. */
    public List<Entry> entries() {
        List<Mintable> sorted = new ArrayList<>(mintables);
        sorted.sort(Comparator.comparingInt(Mintable::tileRank)); // stable — ties keep registry order
        Library library = content.library();
        List<Entry> out = new ArrayList<>();
        for (Mintable mintable : sorted) {
            out.addAll(mintable.tiles(library));
        }
        return out;
    }
}
