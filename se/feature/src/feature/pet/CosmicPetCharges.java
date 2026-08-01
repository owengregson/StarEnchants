package feature.pet;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** One-shot Cosmic pet modifiers consumed by the SE transaction they enhance. */
public final class CosmicPetCharges {

    private static final Map<UUID, Integer> BLACKSCROLL = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ENCHANTER = new ConcurrentHashMap<>();

    private CosmicPetCharges() {
    }

    public static boolean armBlackscroll(UUID player, int bonus) {
        return player != null && bonus > 0 && BLACKSCROLL.putIfAbsent(player, bonus) == null;
    }

    public static boolean armEnchanter(UUID player, int bonus) {
        return player != null && bonus > 0 && ENCHANTER.putIfAbsent(player, bonus) == null;
    }

    public static boolean hasBlackscroll(UUID player) {
        return player != null && BLACKSCROLL.containsKey(player);
    }

    public static boolean hasEnchanter(UUID player) {
        return player != null && ENCHANTER.containsKey(player);
    }

    public static int consumeBlackscroll(UUID player) {
        Integer bonus = player == null ? null : BLACKSCROLL.remove(player);
        return bonus == null ? 0 : bonus;
    }

    public static int consumeEnchanter(UUID player) {
        Integer bonus = player == null ? null : ENCHANTER.remove(player);
        return bonus == null ? 0 : bonus;
    }

    public static void clear(UUID player) {
        BLACKSCROLL.remove(player);
        ENCHANTER.remove(player);
    }

    public static void clearAll() {
        BLACKSCROLL.clear();
        ENCHANTER.clear();
    }
}
