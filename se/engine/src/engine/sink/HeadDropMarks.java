package engine.sink;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player, per-enchant head-drop marks consumed atomically on the marked player's next death. */
public final class HeadDropMarks {

    private HeadDropMarks() {
    }

    private static final ConcurrentHashMap<UUID, Set<String>> MARKS = new ConcurrentHashMap<>();

    public static void mark(UUID player, String channel) {
        if (player == null || channel == null || channel.isBlank()) {
            return;
        }
        MARKS.computeIfAbsent(player, ignored -> ConcurrentHashMap.newKeySet()).add(channel);
    }

    /** Remove all marks for a player and return how many independent head drops they represent. */
    public static int consume(UUID player) {
        Set<String> marks = player == null ? null : MARKS.remove(player);
        return marks == null ? 0 : marks.size();
    }

    public static void clearAll() {
        MARKS.clear();
    }
}
