package feature.combat;

import engine.interact.DamageFold;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import platform.text.Colors;

/**
 * The {@code /se damagedebug} per-hit fold readout: for every hit a toggled player lands or takes, print
 * the damage arbiter's actual buckets (the base the fold saw, the summed percents/flats, the scale, the
 * committed result). Exists to verify and calibrate the combat economy on a REAL server, where an unknown
 * stage (vanilla armor, or a combat plugin's own reduction pass) may run before our fold reads the event
 * (ADR-0050 R3) — the readout makes that pipeline observable instead of inferred.
 *
 * <p>Replies bypass the lang layer deliberately: this is operator diagnostics on the {@code /se} surface
 * (the documented raw-sendMessage exemption, ADR-0041), not player-facing feedback.
 *
 * <p>{@link #report} runs on the firing region thread; both parties belong to that event, so messaging
 * them here is region-correct. The set is concurrent because toggles arrive on the command thread.
 */
public final class DamageDebug {

    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    /** Toggle the per-hit readout for one player; returns the new state (true = now on). */
    public boolean toggle(UUID id) {
        if (enabled.remove(id)) {
            return false;
        }
        enabled.add(id);
        return true;
    }

    /** One committed hit: send the fold line to whichever toggled parties are players. Firing thread. */
    void report(Player attacker, Player victim, double base, DamageFold fold, double scale,
                double folded, double committed, double finalDamage, boolean cancelled) {
        boolean toAttacker = attacker != null && enabled.contains(attacker.getUniqueId());
        boolean toVictim = victim != null && enabled.contains(victim.getUniqueId());
        if (!toAttacker && !toVictim) {
            return;
        }
        // "hits N" = event.getFinalDamage() post-commit: the health the victim actually loses after every
        // modifier still on the event (vanilla armor, a combat plugin's era rewrite) — the number that closes
        // the calibration loop between the fold's commit and what players see (ADR-0050 R4).
        String line = Colors.translate(String.format(Locale.ROOT,
                "&8[&6dmg&8] &7base &f%.2f &8| &7+dmg &f%.0f%%&7, flat &f%.2f &8(x&f%.1f&8) &8| "
                        + "&7red &f%.0f%%&7, flatred &f%.2f &8| &6-> %.2f%s &7hits &c%.2f%s",
                base, fold.outgoingPercent() * 100.0, fold.flatDamage(), scale,
                fold.reductionPercent() * 100.0, fold.flatReduction(), committed,
                committed != folded ? " &8(capped)" : "", finalDamage,
                cancelled ? " &8(&7CANCELLED&8)" : ""));
        if (toAttacker) {
            attacker.sendMessage(line);
        }
        if (toVictim) {
            victim.sendMessage(line);
        }
    }
}
