package feature.trigger;

import compile.load.ContentHolder;
import engine.sink.PermanentPotions;
import engine.stores.SuppressionStore;
import item.worn.WornState;
import item.worn.WornStateStore;
import java.util.function.LongSupplier;
import java.util.function.ToIntFunction;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * The {@code SinkEnv.permanentPotions} wiring (ADR-0072): which potion effects a player carries because their
 * own gear grants them permanently, so a cleanse spares those and lifts only what an opponent landed.
 *
 * <p>The {@link LightningBoost} shape — read on demand from live {@link WornState} + live suppression rather
 * than from a driver's cache. That matters here: {@link PassiveEffectDriver} applies its grants on a refresh
 * cycle, so its bookkeeping can trail a gear change by a sweep, while a cleanse must answer against the gear
 * the player is wearing right now. Both consult the same pure {@code computeDesired}, so what this reports and
 * what the driver maintains cannot disagree about what the gear implies.
 *
 * <p>A suppressed passive grants nothing and is therefore NOT permanent — a debuff left standing by a
 * {@code DISABLE_*} window is landed state, and cleansable.
 */
public final class WornPotionGrants {

    private WornPotionGrants() {
    }

    /**
     * The cleanse seam over a player's worn permanent grants. {@code handleOf} interns a live potion type into
     * the compiled handle space ({@code -1} when this version cannot resolve it — then it is not one of ours).
     */
    public static PermanentPotions fn(ContentHolder content, WornStateStore worn, SuppressionStore suppression,
                                      LongSupplier nowTicks, int held, int passive,
                                      ToIntFunction<PotionEffectType> handleOf) {
        return (target, type) -> {
            if (!(target instanceof Player player) || type == null) {
                return false; // only a player wears gear
            }
            int handle = handleOf.applyAsInt(type);
            if (handle < 0) {
                return false;
            }
            return PassiveEffectDriver.computeDesired(worn.get(player.getUniqueId()), content.snapshot(),
                            suppression, player.getUniqueId(), nowTicks.getAsLong(), held, passive)
                    .apply().containsKey(handle);
        };
    }
}
