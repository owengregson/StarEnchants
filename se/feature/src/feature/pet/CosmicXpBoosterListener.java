package feature.pet;

import engine.stores.VarStore;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Cosmic XP Booster semantics: multiply the XP orbs produced by an entity death at NORMAL priority,
 * truncating the product to an integer. The timed multiplier is authored by the pet's SET_VAR effect.
 */
public final class CosmicXpBoosterListener implements Listener {

    static final String BOOST_VAR = "cosmic-xp-boost";

    private final VarStore vars;
    private final LongSupplier nowTicks;

    public CosmicXpBoosterListener(VarStore vars, LongSupplier nowTicks) {
        this.vars = Objects.requireNonNull(vars, "vars");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        String raw = vars.get(killer.getUniqueId(), BOOST_VAR, nowTicks.getAsLong());
        if (raw == null) {
            return;
        }
        try {
            double multiplier = Double.parseDouble(raw);
            if (Double.isFinite(multiplier) && multiplier >= 0.0) {
                event.setDroppedExp((int) (event.getDroppedExp() * multiplier));
            }
        } catch (NumberFormatException ignored) {
            // Compiler-authored values are numeric; a corrupt runtime value is inert.
        }
    }
}
