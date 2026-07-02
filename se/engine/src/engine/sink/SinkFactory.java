package engine.sink;

import engine.stores.ImmuneStore;
import engine.stores.KeepOnDeathStore;
import engine.stores.KnockbackControlStore;
import engine.stores.SuppressionStore;
import engine.stores.TeleblockStore;
import engine.stores.VarStore;
import java.util.function.LongSupplier;
import platform.economy.EconomyService;

/**
 * Builds a per-event {@link SinkReadback} from the shared collaborators. The seam that keeps the feature
 * dispatchers free of the version-specific resolver type: the modern impl wraps {@code RuntimeHandles}, the
 * legacy impl wraps {@code RenameResolvers} (the modern {@code RuntimeHandles} does not exist on 1.8 —
 * docs/legacy-1.8.9-codeshare-design.md §3.5/§4). The dispatchers hold a {@code SinkFactory} and call
 * {@link #create}; the composition root supplies the right impl for the target.
 */
public interface SinkFactory {

    SinkReadback create(EconomyService economy, SoulDebit souls, VarStore vars, SuppressionStore suppression,
                        KnockbackControlStore knockback, KeepOnDeathStore keepOnDeath, TeleblockStore teleblock,
                        ImmuneStore immune, LongSupplier nowTicks);
}
