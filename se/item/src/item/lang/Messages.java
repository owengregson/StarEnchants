package item.lang;

import compile.load.Lang;
import item.mint.ItemFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.entity.Player;

/**
 * The Bukkit send-boundary facade over the live {@link Lang} catalogue (docs/v3-directives.md §L). It looks
 * up a message key, substitutes its {@code {TOKEN}} placeholders (in {@link Lang}, pure), then translates the
 * legacy {@code &} colour codes to {@code §} via {@link ItemFactory#color} — the one step that needs the
 * server API, which is why {@link Lang} itself stays Bukkit-free in {@code compile}.
 *
 * <p>Constructed once over a {@code Supplier<Lang>} reading the published {@code LangHolder}, so a
 * {@code /se reload} that swaps the catalogue is reflected on the next {@link #format} with no re-wiring.
 * {@link #defaults()} wraps {@link Lang#defaults()} for tests/fixtures and the delegating service ctors, so
 * existing call sites that gain a {@code Messages} parameter compile unchanged and render the same text.
 */
public final class Messages {

    private final Supplier<Lang> lang;

    public Messages(Supplier<Lang> lang) {
        this.lang = Objects.requireNonNull(lang, "lang");
    }

    /** A messages facade over the built-in {@link Lang#defaults()} — the test/fixture/default-ctor source. */
    public static Messages defaults() {
        return new Messages(Lang::defaults);
    }

    /** The colour-translated message for {@code key} with {@code {TOKEN}} placeholders filled from {@code kv}. */
    public String format(String key, Object... kv) {
        return ItemFactory.color(lang.get().format(key, kv));
    }

    /** The colour-translated multi-line block for {@code key}, placeholders filled from {@code kv}. */
    public List<String> lines(String key, Object... kv) {
        List<String> raw = lang.get().lines(key, kv);
        List<String> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(ItemFactory.color(line));
        }
        return out;
    }

    /** Send {@code key} to {@code player} (caller is responsible for being on the player's region thread). */
    public void send(Player player, String key, Object... kv) {
        player.sendMessage(format(key, kv));
    }
}
