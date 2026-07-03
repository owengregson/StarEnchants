package engine.sink;

import engine.stores.EngineStores;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import platform.economy.EconomyService;

/**
 * The per-boot sink wiring — built ONCE in the composition root and threaded to both dispatchers, so a
 * sink write and its separate-event reader can never see different stores (the split-brain the telescoping
 * ctors allowed).
 *
 * <p>{@code movementExemption} is the §N anti-cheat hook (ADR-0027, ADR-0047): a bundled anti-cheat bridge
 * briefly exempts a player before StarEnchants moves them (VELOCITY/TELEPORT), preventing false flags. It
 * rides the env as instance wiring rather than a mutable static installer; {@link #of} supplies the inert
 * no-op default for the many non-root construction sites (tests, tester suites) that integrate no anti-cheat.
 *
 * <p>{@code tempBlocks} is the ONE per-boot {@link TempBlockLedger} shared across every per-event sink, so
 * overlapping temp-block placements from separate activations compound instead of clobbering (a fresh
 * per-event ledger could not — the sink is allocated per activation). {@code trails} is the ONE per-boot
 * {@link TrailWalker} for the same reason: the footprint snake's path memory must survive across the SEPARATE
 * activations a REPEATING trigger fires. Both shared via the env like the stores, never a mutable static.
 */
public record SinkEnv(EconomyService economy, SoulDebit souls, EngineStores stores, LongSupplier nowTicks,
                      Consumer<Player> movementExemption, TempBlockLedger<BlockState> tempBlocks,
                      TrailWalker trails) {

    public SinkEnv {
        Objects.requireNonNull(economy, "economy");
        Objects.requireNonNull(souls, "souls");
        Objects.requireNonNull(stores, "stores");
        Objects.requireNonNull(nowTicks, "nowTicks");
        Objects.requireNonNull(movementExemption, "movementExemption");
        Objects.requireNonNull(tempBlocks, "tempBlocks");
        Objects.requireNonNull(trails, "trails");
    }

    /** The four-arg shape every non-root site used before the exemption rode the env — no-op movement hook. */
    public static SinkEnv of(EconomyService economy, SoulDebit souls, EngineStores stores, LongSupplier nowTicks) {
        return of(economy, souls, stores, nowTicks, player -> { });
    }

    /** The composition-root shape: an anti-cheat exemption plus a fresh per-boot temp-block ledger and trail memory. */
    public static SinkEnv of(EconomyService economy, SoulDebit souls, EngineStores stores, LongSupplier nowTicks,
                             Consumer<Player> movementExemption) {
        return new SinkEnv(economy, souls, stores, nowTicks, movementExemption, BukkitBlockOps.ledger(),
                new TrailWalker());
    }
}
