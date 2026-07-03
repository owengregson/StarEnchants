package feature.menu;

import compile.load.Library;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * ONE declaration per mintable item type (ADR-0047) — the single source the three mint surfaces derive from:
 * the operator mint-menu tiles, {@code /se give <type>}, and the {@code /se <type>} self-mint rows. The
 * give/self bodies are the former {@code SeCommand} helpers moved verbatim (same messages, same arg parsing),
 * closed over their services at module construction; nothing here re-derives a parse or reword a message.
 */
public interface Mintable {

    /** The canonical {@code /se give} key ({@code "blackscroll"}); unique, lower-case. */
    String type();

    /** Accepted aliases (e.g. {@code "upgrade"} → heroic); empty when the type has none. */
    default List<String> aliases() {
        return List.of();
    }

    /** {@code /se give <type> <player> [args…]} — {@code args} indexed as received (type at 1, player at 2). */
    void give(CommandSender sender, Player target, String[] args, MintIo io);

    /** {@code /se <type> [args…]} self-mint, or {@code null} for no top-level row (extractor, set, the traks). */
    default SelfMint self() {
        return null;
    }

    /** The self-mint body for the {@code /se <type>} row. */
    interface SelfMint {
        void run(CommandSender sender, String[] args, MintIo io);
    }

    /** Mint-menu tiles (label + no-arg thunk); empty = not tile-minted (book, set, crystal-by-key). */
    default List<MintCatalog.Entry> tiles(Library library) {
        return List.of();
    }

    /** Preserves the shipped curated tile order; ties broken by registry order. Default = last. */
    default int tileRank() {
        return Integer.MAX_VALUE;
    }

    /** Tab completion for {@code /se give <type> <player> <argIndex≥3>} (crystal keys, tiers, set members, …). */
    default List<String> completeGiveArg(int argIndex, Library library) {
        return List.of();
    }
}
