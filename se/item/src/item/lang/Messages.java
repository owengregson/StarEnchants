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
    private final Supplier<String> prefix;                       // §L config.yml messages.prefix (live)
    private final java.util.function.BooleanSupplier feedback;   // §L config.yml messages.feedback (live)
    // §N optional PlaceholderAPI passthrough (ADR-0027): resolves other plugins' %…% in a message for its
    // target player. Identity by default (PAPI absent / tests); the composition root injects the live one.
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
     * Canonical form (the composition root): {@code prefix} is prepended to every {@link #format} result,
     * {@code feedback} gates {@link #send} (the keyed gameplay-feedback channel), and {@code placeholders}
     * resolves other plugins' tokens in a sent message for its target player (identity when PlaceholderAPI is
     * absent). All read live, so a {@code /se reload} re-tunes them.
     */
    public Messages(Supplier<Lang> lang, Supplier<String> prefix, java.util.function.BooleanSupplier feedback,
                    java.util.function.BiFunction<Player, String, String> placeholders) {
        this.lang = Objects.requireNonNull(lang, "lang");
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.placeholders = Objects.requireNonNull(placeholders, "placeholders");
    }

    /** A messages facade over the built-in {@link Lang#defaults()} — the test/fixture/default-ctor source. */
    public static Messages defaults() {
        return new Messages(Lang::defaults);
    }

    /**
     * The colour-translated message for {@code key} with {@code {TOKEN}} placeholders filled from {@code kv},
     * prepended with the configured {@code messages.prefix} (empty by default). Used for all single-line
     * player feedback — never for item names or lore (those render through {@code LoreRenderer}).
     */
    public String format(String key, Object... kv) {
        String p = prefix.get();
        String body = lang.get().format(key, kv);
        return ItemFactory.color(p == null || p.isEmpty() ? body : p + body);
    }

    /** Whether the keyed gameplay-feedback channel ({@link #send}) is enabled (config.yml messages.feedback). */
    public boolean feedbackEnabled() {
        return feedback.getAsBoolean();
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

    /**
     * Send {@code key} to {@code player} on the keyed gameplay-feedback channel — a no-op when
     * {@code messages.feedback} is off (a quiet server). Caller is responsible for being on the player's
     * region thread.
     */
    public void send(Player player, String key, Object... kv) {
        if (!feedback.getAsBoolean()) {
            return;
        }
        // Resolve any other-plugin %…% placeholders for this player (§N PlaceholderAPI passthrough); identity
        // when PAPI is absent, so this is a no-op cost on a server without it.
        player.sendMessage(placeholders.apply(player, format(key, kv)));
    }
}
