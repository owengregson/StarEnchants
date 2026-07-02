package engine.sink;

import engine.stores.ImmuneStore;
import engine.stores.KeepOnDeathStore;
import engine.stores.KnockbackControlStore;
import engine.stores.SuppressionStore;
import engine.stores.TeleblockStore;
import engine.stores.VarStore;
import java.util.function.LongSupplier;
import platform.economy.EconomyService;
import platform.resolve.RuntimeHandles;

/**
 * Modern {@link SinkFactory}: wraps {@code RuntimeHandles} (the id&rarr;live-object resolver) and builds the
 * modern {@link DispatchSink}. Same-FQN counterpart to the {@code overlay/legacy} impl (which wraps
 * {@code RenameResolvers}); selected at build assembly.
 */
public final class DispatchSinkFactory implements SinkFactory {

    private final RuntimeHandles handles;

    public DispatchSinkFactory(RuntimeHandles handles) {
        this.handles = handles;
    }

    @Override
    public SinkReadback create(EconomyService economy, SoulDebit souls, VarStore vars, SuppressionStore suppression,
                               KnockbackControlStore knockback, KeepOnDeathStore keepOnDeath, TeleblockStore teleblock,
                               ImmuneStore immune, LongSupplier nowTicks) {
        return new DispatchSink(handles, economy, souls, vars, suppression, knockback, keepOnDeath,
                teleblock, immune, nowTicks);
    }
}
