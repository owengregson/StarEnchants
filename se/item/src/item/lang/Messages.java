package item.lang;

import compile.load.Lang;
import item.mint.ItemFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.entity.Player;

/**
 * Bukkit send-boundary over the live {@link Lang} catalogue (§L): looks up a key, fills its {@code {TOKEN}}
 * placeholders (in {@link Lang}, pure), then translates {@code &}→{@code §} via {@link ItemFactory#color} —
 * the only server-API step, which is why {@link Lang} stays Bukkit-free in {@code compile}.
 *
 * <p>Reads its {@code Supplier<Lang>} live, so a {@code /se reload} that swaps the catalogue takes effect on
 * the next {@link #format}.
 */
public final class Messages {

    private final Supplier<Lang> lang;
    private final Supplier<String> prefix;                       // §L config.yml messages.prefix (live)
    private final java.util.function.BooleanSupplier feedback;   // §L config.yml messages.feedback (live)
    // §N PlaceholderAPI passthrough (ADR-0027): identity by default (PAPI absent/tests); root injects live.
    private final java.util.function.BiFunction<Player, String, String> placeholders;

    /** No-prefix, feedback-on, no-passthrough form (tests/fixtures + the delegating service ctors). */
    public Messages(Supplier<Lang> lang) {
        this(lang, () -> "", () -> true);
    }

    /** Prefix + feedback, no placeholder passthrough — kept so existing call sites compile unchanged. */
    public Messages(Supplier<Lang> lang, Supplier<String> prefix, java.util.function.BooleanSupplier feedback) {
        this(lang, prefix, feedback, (player, text) -> text);
    }

    /**
     * Canonical form (composition root): {@code prefix} is prepended to every {@link #format}, {@code feedback}
     * gates {@link #send}, {@code placeholders} resolves other plugins' tokens for the target player (identity
     * when PlaceholderAPI is absent). All read live so a {@code /se reload} re-tunes them.
     */
    public Messages(Supplier<Lang> lang, Supplier<String> prefix, java.util.function.BooleanSupplier feedback,
                    java.util.function.BiFunction<Player, String, String> placeholders) {
        this.lang = Objects.requireNonNull(lang, "lang");
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.placeholders = Objects.requireNonNull(placeholders, "placeholders");
    }

    /** Facade over the built-in {@link Lang#defaults()} — test/fixture/default-ctor source. */
    public static Messages defaults() {
        return new Messages(Lang::defaults);
    }

    /** Single-line player feedback only — item names/lore go through {@code LoreRenderer}, never here. */
    public String format(String key, Object... kv) {
        String p = prefix.get();
        String body = lang.get().format(key, kv);
        return ItemFactory.color(p == null || p.isEmpty() ? body : p + body);
    }

    public boolean feedbackEnabled() {
        return feedback.getAsBoolean();
    }

    public List<String> lines(String key, Object... kv) {
        List<String> raw = lang.get().lines(key, kv);
        List<String> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(ItemFactory.color(line));
        }
        return out;
    }

    /** No-op when {@code messages.feedback} is off. Caller must be on the player's region thread (Folia). */
    public void send(Player player, String key, Object... kv) {
        if (!feedback.getAsBoolean()) {
            return;
        }
        // §N PlaceholderAPI passthrough; identity (no-op cost) when PAPI is absent.
        player.sendMessage(placeholders.apply(player, format(key, kv)));
    }
}
