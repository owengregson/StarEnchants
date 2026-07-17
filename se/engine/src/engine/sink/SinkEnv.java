package engine.sink;

import engine.stores.EngineStores;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.function.ToDoubleFunction;
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
 * activations a REPEATING trigger fires. {@code timedReverts} is the ONE per-boot {@link TimedRevert} for the
 * same reason: a timed buff's revert closure must outlive the per-event sink so the quit drain can run it when
 * a player logs out mid-window (F07/F08). All three shared via the env like the stores, never a mutable static.
 */
public record SinkEnv(EconomyService economy, SoulDebit souls, EngineStores stores, LongSupplier nowTicks,
                      Consumer<Player> movementExemption, TempBlockLedger<BlockState> tempBlocks,
                      TrailWalker trails, TimedRevert timedReverts,
                      DoubleSupplier moneyInterestCap, GearProtection gearProtection,
                      ToDoubleFunction<UUID> lightningBoost) {

    public SinkEnv {
        Objects.requireNonNull(economy, "economy");
        Objects.requireNonNull(souls, "souls");
        Objects.requireNonNull(stores, "stores");
        Objects.requireNonNull(nowTicks, "nowTicks");
        Objects.requireNonNull(movementExemption, "movementExemption");
        Objects.requireNonNull(tempBlocks, "tempBlocks");
        Objects.requireNonNull(trails, "trails");
        Objects.requireNonNull(timedReverts, "timedReverts");
        Objects.requireNonNull(moneyInterestCap, "moneyInterestCap");
        Objects.requireNonNull(gearProtection, "gearProtection");
        Objects.requireNonNull(lightningBoost, "lightningBoost");
    }

    /** The four-arg shape every non-root site used before the exemption rode the env — no-op movement hook. */
    public static SinkEnv of(EconomyService economy, SoulDebit souls, EngineStores stores, LongSupplier nowTicks) {
        return of(economy, souls, stores, nowTicks, player -> { });
    }

    /** The composition-root shape: an anti-cheat exemption plus a fresh per-boot temp-block ledger and trail memory. */
    public static SinkEnv of(EconomyService economy, SoulDebit souls, EngineStores stores, LongSupplier nowTicks,
                             Consumer<Player> movementExemption) {
        return of(economy, souls, stores, nowTicks, movementExemption, () -> 0, GearProtection.NONE);
    }

    /**
     * The full composition-root shape (ADR-0052): {@code moneyInterestCap} is the LIVE ceiling on one
     * {@code interest_percent} deposit ({@code <= 0} = uncapped, read per use so {@code /se reload} re-tunes
     * it); {@code gearProtection} is the scroll-marker seam {@code STRIP_SCROLL} mutates victim gear through.
     */
    public static SinkEnv of(EconomyService economy, SoulDebit souls, EngineStores stores, LongSupplier nowTicks,
                             Consumer<Player> movementExemption, DoubleSupplier moneyInterestCap,
                             GearProtection gearProtection) {
        return of(economy, souls, stores, nowTicks, movementExemption, moneyInterestCap, gearProtection,
                id -> 0.0);
    }

    /**
     * The ADR-0063 shape: {@code lightningBoost} is the worn LIGHTNING_MOD channel — actor UUID → summed
     * boost fraction, read per bolt emit (live WornState + suppression; {@code id -> 0.0} = no channel).
     */
    public static SinkEnv of(EconomyService economy, SoulDebit souls, EngineStores stores, LongSupplier nowTicks,
                             Consumer<Player> movementExemption, DoubleSupplier moneyInterestCap,
                             GearProtection gearProtection, ToDoubleFunction<UUID> lightningBoost) {
        return new SinkEnv(economy, souls, stores, nowTicks, movementExemption, BukkitBlockOps.ledger(),
                new TrailWalker(), new TimedRevert(), moneyInterestCap, gearProtection, lightningBoost);
    }
}
