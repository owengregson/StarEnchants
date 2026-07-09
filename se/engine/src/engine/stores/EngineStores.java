package engine.stores;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Every per-player engine store as ONE aggregate (docs/architecture.md §5.4). The single quit-cleanup
 * authority iterates {@link #all()}, so a store added here structurally cannot miss cleanup
 * ({@code EngineStoresTest} pins each record component to membership in {@link #all()}).
 *
 * <p>Component order is preserved from the old {@code EngineStoreListener} sweep (vars &rarr; &hellip;
 * &rarr; combo). {@code engine.stores} is a hot-path package (EngineBoundaryArchTest) — no
 * String.split/Pattern.compile/ItemStack.clone/gson/snakeyaml/platform.sched here.
 */
public record EngineStores(
        VarStore vars, SuppressionStore suppression, KnockbackControlStore knockback,
        KeepOnDeathStore keepOnDeath, TeleblockStore teleblock, ImmuneStore immune,
        CooldownStore cooldowns, ComboStore combo, WhyStore why) {

    public EngineStores {
        Objects.requireNonNull(vars, "vars");
        Objects.requireNonNull(suppression, "suppression");
        Objects.requireNonNull(knockback, "knockback");
        Objects.requireNonNull(keepOnDeath, "keepOnDeath");
        Objects.requireNonNull(teleblock, "teleblock");
        Objects.requireNonNull(immune, "immune");
        Objects.requireNonNull(cooldowns, "cooldowns");
        Objects.requireNonNull(combo, "combo");
        Objects.requireNonNull(why, "why");
    }

    /** A fresh aggregate with every store newly constructed (the composition-root default). */
    public static EngineStores fresh() {
        return new EngineStores(new VarStore(), new SuppressionStore(), new KnockbackControlStore(),
                new KeepOnDeathStore(), new TeleblockStore(), new ImmuneStore(),
                new CooldownStore(), new ComboStore(), new WhyStore());
    }

    /** Every store as the {@link PlayerScoped} seam, in sweep order. */
    public List<PlayerScoped> all() {
        return List.of(vars, suppression, knockback, keepOnDeath, teleblock, immune, cooldowns, combo, why);
    }

    /**
     * The stores forgotten WHOLESALE on quit: private/transient/diagnostic state that a relog should not carry
     * (writable vars, knockback control, keep-on-death, damage immunity, combo streak, the /se why ring). Clearing
     * these on quit is the conservative direction — worn-derived buffs re-establish on rejoin.
     */
    public List<PlayerScoped> quitVolatile() {
        return List.of(vars, knockback, keepOnDeath, immune, combo, why);
    }

    /**
     * The combat-integrity stores RETAINED across a relog: cooldowns and victim-applied teleblock/suppression
     * windows. Only their already-elapsed entries are shed on quit, so a ~5-10s disconnect+reconnect cannot skip a
     * live cooldown or shed a landed debuff (the monotonic tick keeps a surviving absolute expiry valid on rejoin).
     */
    public List<RetainedStore> quitRetained() {
        return List.of(cooldowns, teleblock, suppression);
    }

    /**
     * The quit sweep: clear the {@link #quitVolatile} stores, drop the self-derived suppression immunity (windows
     * survive), and evict only the {@link #quitRetained} stores' elapsed entries at {@code nowTicks}. A full clear
     * is {@link #clearAll(UUID)}.
     */
    public void quitSweep(UUID player, long nowTicks) {
        for (PlayerScoped store : quitVolatile()) {
            store.clear(player);
        }
        suppression.clearImmunity(player); // self-state re-derived on rejoin; DISABLE_* windows survive below
        for (RetainedStore store : quitRetained()) {
            store.evictElapsed(player, nowTicks);
        }
    }

    /** Drop every retained store's elapsed entries across all players at {@code nowTicks} (the offline-state sweep). */
    public void evictElapsed(long nowTicks) {
        for (RetainedStore store : quitRetained()) {
            store.evictElapsed(nowTicks);
        }
    }

    /** Forget every store's state for one player (a full clear, e.g. on disable). */
    public void clearAll(UUID player) {
        for (PlayerScoped store : all()) {
            store.clear(player);
        }
    }
}
