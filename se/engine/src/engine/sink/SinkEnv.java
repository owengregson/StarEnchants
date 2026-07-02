package engine.sink;

import engine.stores.EngineStores;
import java.util.Objects;
import java.util.function.LongSupplier;
import platform.economy.EconomyService;

/**
 * The per-boot sink wiring — built ONCE in the composition root and threaded to both dispatchers, so a
 * sink write and its separate-event reader can never see different stores (the split-brain the telescoping
 * ctors allowed).
 */
public record SinkEnv(EconomyService economy, SoulDebit souls, EngineStores stores, LongSupplier nowTicks) {

    public SinkEnv {
        Objects.requireNonNull(economy, "economy");
        Objects.requireNonNull(souls, "souls");
        Objects.requireNonNull(stores, "stores");
        Objects.requireNonNull(nowTicks, "nowTicks");
    }
}
