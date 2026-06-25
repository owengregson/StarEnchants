package feature.trigger;

import engine.stores.ImmuneStore;
import engine.stores.KeepOnDeathStore;
import engine.stores.KnockbackControlStore;
import engine.stores.SuppressionStore;
import engine.stores.TeleblockStore;
import engine.stores.VarStore;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Central quit hook clearing a player's per-player engine store state (§5.4): {@link VarStore},
 * {@link SuppressionStore}, {@link KnockbackControlStore}, {@link KeepOnDeathStore}, {@link TeleblockStore},
 * {@link ImmuneStore}. All self-bound via lazy TTL eviction on read; this is the upper bound that frees a
 * player's entries the moment they leave. Folia-safe: the stores are concurrent.
 */
public final class EngineStoreListener implements Listener {

    private final VarStore vars;
    private final SuppressionStore suppression;
    private final KnockbackControlStore knockback;
    private final KeepOnDeathStore keepOnDeath;
    private final TeleblockStore teleblock;
    private final ImmuneStore immune;

    public EngineStoreListener(VarStore vars, SuppressionStore suppression, KnockbackControlStore knockback,
                               KeepOnDeathStore keepOnDeath, TeleblockStore teleblock, ImmuneStore immune) {
        this.vars = Objects.requireNonNull(vars, "vars");
        this.suppression = Objects.requireNonNull(suppression, "suppression");
        this.knockback = Objects.requireNonNull(knockback, "knockback");
        this.keepOnDeath = Objects.requireNonNull(keepOnDeath, "keepOnDeath");
        this.teleblock = Objects.requireNonNull(teleblock, "teleblock");
        this.immune = Objects.requireNonNull(immune, "immune");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        vars.clear(id);
        suppression.clear(id);
        knockback.clear(id);
        keepOnDeath.clear(id);
        teleblock.clear(id);
        immune.clear(id);
    }
}
