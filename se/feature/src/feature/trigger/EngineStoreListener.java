package feature.trigger;

import engine.stores.ComboStore;
import engine.stores.CooldownStore;
import engine.stores.ImmuneStore;
import engine.stores.KeepOnDeathStore;
import engine.stores.KnockbackControlStore;
import engine.stores.SuppressionStore;
import engine.stores.TeleblockStore;
import engine.stores.VarStore;
import feature.soul.SoulService;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * The single quit-cleanup authority for per-player engine state (§5.4): frees a leaving player's entries in
 * every store the moment they leave. The stores also self-bound via lazy TTL eviction on read; this is the
 * upper bound. Every UUID-keyed store the runtime keeps belongs here — a store not wired here can only shed a
 * quitter's entry by TTL, which for an unbounded cache (e.g. the soul total) never happens.
 */
public final class EngineStoreListener implements Listener {

    private final VarStore vars;
    private final SuppressionStore suppression;
    private final KnockbackControlStore knockback;
    private final KeepOnDeathStore keepOnDeath;
    private final TeleblockStore teleblock;
    private final ImmuneStore immune;
    private final CooldownStore cooldowns;
    private final ComboStore combo;
    private final SoulService souls;

    public EngineStoreListener(VarStore vars, SuppressionStore suppression, KnockbackControlStore knockback,
                               KeepOnDeathStore keepOnDeath, TeleblockStore teleblock, ImmuneStore immune,
                               CooldownStore cooldowns, ComboStore combo, SoulService souls) {
        this.vars = Objects.requireNonNull(vars, "vars");
        this.suppression = Objects.requireNonNull(suppression, "suppression");
        this.knockback = Objects.requireNonNull(knockback, "knockback");
        this.keepOnDeath = Objects.requireNonNull(keepOnDeath, "keepOnDeath");
        this.teleblock = Objects.requireNonNull(teleblock, "teleblock");
        this.immune = Objects.requireNonNull(immune, "immune");
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.combo = Objects.requireNonNull(combo, "combo");
        this.souls = Objects.requireNonNull(souls, "souls");
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
        cooldowns.clear(id);
        combo.clear(id);
        souls.evictCache(id); // SoulListener.clear flushes owed drain + mode; this frees the cached total
    }
}
