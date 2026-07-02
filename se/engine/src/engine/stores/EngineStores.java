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

    /** Forget every store's state for one player (the quit sweep). */
    public void clearAll(UUID player) {
        for (PlayerScoped store : all()) {
            store.clear(player);
        }
    }
}
