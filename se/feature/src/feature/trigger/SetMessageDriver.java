package feature.trigger;

import compile.load.ContentHolder;
import compile.load.SetDef;
import compile.model.Snapshot;
import item.worn.WornState;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import org.bukkit.entity.Player;

/**
 * Announces an armour set's EQUIP / REMOVE message when it transitions active↔inactive on an equipment change
 * (§6.6). Mirrors {@link LifecycleDriver}: diffs the currently-active set keys against the last-seen set —
 * keyed by STABLE key so it survives a reload (which reassigns dense ids) — and fires the authored per-set
 * message, but only for a set whose config opts in ({@code announce}). The message is authored verbatim per
 * set (no tokens). Must run on the player's own region thread (it messages the player).
 */
public final class SetMessageDriver {

    private final ContentHolder content;
    private final BiConsumer<Player, String> send; // colour-translate + sendMessage, injected (kept off this pure-ish core)
    private final BooleanSupplier uppercase; // auto-capitalise the set message (read live so a reload can flip it)
    private final SetTransition transition; // universal equip/unequip sound+particle (§6.6) — fired before the message gate
    private final Map<UUID, Set<String>> active = new ConcurrentHashMap<>();

    /** Equip/unequip side-effect for a completed set — the universal sound + particle (the message is separate). */
    @FunctionalInterface
    public interface SetTransition {
        SetTransition NONE = (player, def, equipped) -> {};

        void onTransition(Player player, SetDef def, boolean equipped);
    }

    public SetMessageDriver(ContentHolder content, BiConsumer<Player, String> send) {
        this(content, send, () -> false, SetTransition.NONE);
    }

    public SetMessageDriver(ContentHolder content, BiConsumer<Player, String> send,
                            BooleanSupplier uppercase, SetTransition transition) {
        this.content = Objects.requireNonNull(content, "content");
        this.send = Objects.requireNonNull(send, "send");
        this.uppercase = Objects.requireNonNull(uppercase, "uppercase");
        this.transition = Objects.requireNonNull(transition, "transition");
    }

    /**
     * Diff {@code worn}'s active sets against the player's last-seen set and announce the transitions. Must run
     * on the player's own thread. No-op when the worn state is stale against the live snapshot (re-driven next
     * change), so dense set ids resolve to the right keys.
     */
    public void refresh(Player player, WornState worn) {
        if (worn == null) {
            return;
        }
        Snapshot snapshot = content.snapshot();
        if (worn.gen() != snapshot.generation()) {
            return; // worn ids index a different snapshot — skip; the reload re-resolve drives a fresh diff
        }
        UUID id = player.getUniqueId();
        Set<String> now = activeSetKeys(worn, snapshot);
        Set<String> previous = active.getOrDefault(id, Set.of());
        for (String key : now) {
            if (!previous.contains(key)) {
                announce(player, key, true); // newly complete
            }
        }
        for (String key : previous) {
            if (!now.contains(key)) {
                announce(player, key, false); // dropped below threshold
            }
        }
        active.put(id, now);
    }

    /** Drop a player's tracking on quit — no teardown needed. */
    public void clear(UUID player) {
        active.remove(player);
    }

    public void clearAll() {
        active.clear();
    }

    private Set<String> activeSetKeys(WornState worn, Snapshot snapshot) {
        Set<String> keys = new HashSet<>();
        BitSet sets = worn.activeSets();
        for (int setId = sets.nextSetBit(0); setId >= 0; setId = sets.nextSetBit(setId + 1)) {
            String key = snapshot.stableKeys().keyOf(setId);
            if (key != null) {
                keys.add(key);
            }
        }
        return keys;
    }

    private void announce(Player player, String setKey, boolean equipped) {
        SetDef def = content.library().setDefOf(setKey);
        if (def == null) {
            return;
        }
        transition.onTransition(player, def, equipped); // universal FX — independent of whether the set announces
        if (!def.announce()) {
            return;
        }
        String message = equipped ? def.equipMessage() : def.removeMessage();
        if (message != null && !message.isEmpty()) {
            send.accept(player, uppercase.getAsBoolean() ? upperPreservingCodes(message) : message);
        }
    }

    /** Uppercase the VISIBLE text while leaving {@code &}/{@code §} colour-and-format codes untouched. */
    static String upperPreservingCodes(String message) {
        StringBuilder out = new StringBuilder(message.length());
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < message.length()) {
                out.append(c).append(message.charAt(++i)); // keep the code char verbatim (so &a stays &a, not &A)
            } else {
                out.append(Character.toUpperCase(c));
            }
        }
        return out.toString();
    }
}
