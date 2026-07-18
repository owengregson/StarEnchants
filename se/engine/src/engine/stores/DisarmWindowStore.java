package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Unhanding state (ADR-0071 DISARM_SHUFFLE): a one-shot armed window; the holder's next LANDED
 * melee hit on a player within it shuffles the victim's held item and carries the malus.
 * {@link DamageCapStore}'s arm/consume shape, split from it because the consume site and payload
 * differ.
 *
 * <p>Self-armed, self-benefiting combat state — swept VOLATILE on quit. Hot-path package rules
 * apply (concurrent map only).
 */
public final class DisarmWindowStore implements PlayerScoped {

    /** One armed window: the expiry tick and the self-malus fraction the strike carries. */
    public record Arm(long expiryTick, double malusFraction) {
    }

    private final Map<UUID, Arm> armed = new ConcurrentHashMap<>();

    public void arm(UUID holder, long nowTicks, int durationTicks, double malusFraction) {
        if (holder == null || durationTicks <= 0) {
            return;
        }
        armed.put(holder, new Arm(nowTicks + durationTicks, malusFraction));
    }

    /** The live arm at {@code nowTicks} or {@code null}; does NOT consume (a cancelled hit keeps it). */
    public Arm armed(UUID holder, long nowTicks) {
        Arm a = armed.get(holder);
        if (a == null) {
            return null;
        }
        if (nowTicks >= a.expiryTick()) {
            armed.remove(holder, a);
            return null;
        }
        return a;
    }

    /** One-shot consume (the MONITOR landed-hit commit). */
    public void consume(UUID holder) {
        armed.remove(holder);
    }

    @Override
    public void clear(UUID player) {
        armed.remove(player);
    }

    /** Forget every player's window (call on disable). */
    public void clearAll() {
        armed.clear();
    }
}
