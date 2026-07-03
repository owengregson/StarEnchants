package bootstrap.wire;

import compile.load.Library;
import feature.menu.MintCatalog;
import feature.menu.MintIo;
import feature.menu.Mintable;
import java.util.List;
import java.util.Objects;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * A {@link Mintable} assembled from a module's closures (ADR-0047): the ONE mint declaration whose give/self
 * bodies moved verbatim from {@code SeCommand}, closed over their feature services at module construction. The
 * three mint surfaces (mint-menu tiles, {@code /se give <type>}, the {@code /se <type>} self-mint row) all
 * derive from these declarations; {@code Mints} holds the factories, a module just declares which it owns.
 */
final class Mint implements Mintable {

    @FunctionalInterface
    interface GiveBody {
        void give(CommandSender sender, Player target, String[] args, MintIo io);
    }

    @FunctionalInterface
    interface TilesBody {
        List<MintCatalog.Entry> tiles(Library library);
    }

    private final String type;
    private final List<String> aliases;
    private final GiveBody give;
    private final SelfMint self;   // nullable — no top-level /se <type> row
    private final int tileRank;
    private final TilesBody tiles;  // nullable — not tile-minted

    private Mint(Builder b) {
        this.type = Objects.requireNonNull(b.type, "type");
        this.aliases = List.copyOf(b.aliases);
        this.give = Objects.requireNonNull(b.give, "give");
        this.self = b.self;
        this.tileRank = b.tileRank;
        this.tiles = b.tiles;
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public List<String> aliases() {
        return aliases;
    }

    @Override
    public void give(CommandSender sender, Player target, String[] args, MintIo io) {
        give.give(sender, target, args, io);
    }

    @Override
    public SelfMint self() {
        return self;
    }

    @Override
    public List<MintCatalog.Entry> tiles(Library library) {
        return tiles == null ? List.of() : tiles.tiles(library);
    }

    @Override
    public int tileRank() {
        return tiles == null ? Integer.MAX_VALUE : tileRank;
    }

    static Builder type(String type) {
        return new Builder(type);
    }

    static final class Builder {

        private final String type;
        private List<String> aliases = List.of();
        private GiveBody give;
        private SelfMint self;
        private int tileRank = Integer.MAX_VALUE;
        private TilesBody tiles;

        private Builder(String type) {
            this.type = type;
        }

        Builder aliases(String... aliases) {
            this.aliases = List.of(aliases);
            return this;
        }

        Builder give(GiveBody give) {
            this.give = give;
            return this;
        }

        Builder self(SelfMint self) {
            this.self = self;
            return this;
        }

        /** The mint-menu tile(s) plus the curated rank (§5) that preserves the shipped {@code entries()} order. */
        Builder tiles(int rank, TilesBody tiles) {
            this.tileRank = rank;
            this.tiles = tiles;
            return this;
        }

        Mint build() {
            return new Mint(this);
        }
    }
}
