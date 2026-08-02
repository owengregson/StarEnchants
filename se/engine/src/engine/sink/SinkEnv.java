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
 * a player logs out mid-window (F07/F08). {@code dotPark} is the ONE per-boot combo-DoT park ledger (ADR-0069),
 * shared so a park and its flush (separate events) see the same buckets. {@code trapStructures} is the ONE
 * per-boot {@link TrapStructures} registry (ADR-0071), shared so a confining placement and its Turnkey break
 * (separate events) see the same structures. {@code permanentPotions} is the ADR-0072 cleanse seam, riding the
 * env for the same reason {@code movementExemption} does — instance wiring, not a mutable static installer.
 * All shared via the env like the stores, never a mutable static.
 */
public record SinkEnv(EconomyService economy, SoulDebit souls, EngineStores stores, LongSupplier nowTicks,
                      Consumer<Player> movementExemption, TempBlockLedger<BlockState> tempBlocks,
                      TrailWalker trails, TimedRevert timedReverts, DotParkLedger dotPark,
                      DoubleSupplier moneyInterestCap, GearProtection gearProtection,
                      ToDoubleFunction<UUID> lightningBoost, TrapStructures trapStructures,
                      PermanentPotions permanentPotions) {

    public SinkEnv {
        Objects.requireNonNull(economy, "economy");
        Objects.requireNonNull(souls, "souls");
        Objects.requireNonNull(stores, "stores");
        Objects.requireNonNull(nowTicks, "nowTicks");
        Objects.requireNonNull(movementExemption, "movementExemption");
        Objects.requireNonNull(tempBlocks, "tempBlocks");
        Objects.requireNonNull(trails, "trails");
        Objects.requireNonNull(timedReverts, "timedReverts");
        Objects.requireNonNull(dotPark, "dotPark");
        Objects.requireNonNull(moneyInterestCap, "moneyInterestCap");
        Objects.requireNonNull(gearProtection, "gearProtection");
        Objects.requireNonNull(lightningBoost, "lightningBoost");
        Objects.requireNonNull(trapStructures, "trapStructures");
        Objects.requireNonNull(permanentPotions, "permanentPotions");
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
        return of(economy, souls, stores, nowTicks, movementExemption, moneyInterestCap, gearProtection,
                lightningBoost, PermanentPotions.NONE);
    }

    /**
     * The ADR-0072 shape: {@code permanentPotions} tells a {@code CURE category: HARMFUL} cleanse which of the
     * holder's harmful effects are permanent-while-worn grants it must spare ({@link PermanentPotions#NONE} =
     * SE claims none, leaving only the duration test).
     */
    public static SinkEnv of(EconomyService economy, SoulDebit souls, EngineStores stores, LongSupplier nowTicks,
                             Consumer<Player> movementExemption, DoubleSupplier moneyInterestCap,
                             GearProtection gearProtection, ToDoubleFunction<UUID> lightningBoost,
                             PermanentPotions permanentPotions) {
        return new SinkEnv(economy, souls, stores, nowTicks, movementExemption, BukkitBlockOps.ledger(),
                new TrailWalker(), new TimedRevert(), new DotParkLedger(), moneyInterestCap, gearProtection,
                lightningBoost, new TrapStructures(), permanentPotions);
    }
}
