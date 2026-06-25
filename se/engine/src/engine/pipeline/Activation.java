package engine.pipeline;

import engine.condition.FactBuffer;
import engine.interact.SoulLedger;
import engine.interact.SuppressionSet;
import java.util.Objects;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import org.bukkit.Location;

/**
 * The per-event context one ability is evaluated against by the {@link ActivationPipeline}
 * (docs/architecture.md §3.3). A trigger listener builds one per Bukkit event (capturing the activator,
 * world, trigger, and the facts/suppression for this hit); the firing System runs every candidate
 * ability through the pipeline against it.
 *
 * <p>Everything here is an immutable per-event input or a reference to scratch the firing thread owns
 * ({@link FactBuffer}, {@link SuppressionSet}); the long-lived stores (cooldowns, souls) belong to the
 * pipeline, not the activation.
 */
public final class Activation {

    private final UUID actor;
    private final int worldId;
    private final int triggerId;
    private final long nowTicks;
    private final FactBuffer facts;
    private final SuppressionSet suppression;
    private final DoubleSupplier chanceRoll;
    private final UUID activeGem;
    private final SoulLedger.Balance gemBalance;
    private final Location location;

    private Activation(Builder b) {
        this.actor = Objects.requireNonNull(b.actor, "actor");
        this.worldId = b.worldId;
        this.triggerId = b.triggerId;
        this.nowTicks = b.nowTicks;
        this.facts = b.facts;
        this.suppression = b.suppression;
        this.chanceRoll = b.chanceRoll;
        this.activeGem = b.activeGem;
        this.gemBalance = b.gemBalance;
        this.location = b.location;
    }

    /**
     * @param actor     the activating player
     * @param worldId   the activator's interned world id (gate 1)
     * @param triggerId the interned trigger id this event maps to (gate 3)
     * @param nowTicks  the current tick (cooldowns, gates 6 + 11)
     */
    public static Builder builder(UUID actor, int worldId, int triggerId, long nowTicks) {
        return new Builder(actor, worldId, triggerId, nowTicks);
    }

    public UUID actor() {
        return actor;
    }

    public int worldId() {
        return worldId;
    }

    public int triggerId() {
        return triggerId;
    }

    public long nowTicks() {
        return nowTicks;
    }

    /** The condition fact buffer (gate 7); never {@code null}. */
    public FactBuffer facts() {
        return facts;
    }

    /** The suppression set relevant to the abilities being gated (gate 5); never {@code null}. */
    public SuppressionSet suppression() {
        return suppression;
    }

    /** Rolls a value in {@code [0,100)} for the chance gate (gate 8). */
    public DoubleSupplier chanceRoll() {
        return chanceRoll;
    }

    /** The active soul gem's id, or {@code null} if the activator is not in soul mode (gate 10). */
    public UUID activeGem() {
        return activeGem;
    }

    /** The active gem's soul balance proxy, or {@code null} if not in soul mode (gate 10). */
    public SoulLedger.Balance gemBalance() {
        return gemBalance;
    }

    /**
     * The captured firing location (gate 2 protection/region), snapshotted on the firing thread (its own
     * region on Folia) so the protection {@code Guard} may query the owning region safely. {@code null}
     * for a non-positional activation (or in tests), which the guard treats as "allow".
     */
    public Location location() {
        return location;
    }

    /**
     * Builds an {@link Activation}. Defaults keep tests/non-combat triggers terse: empty
     * {@link FactBuffer}/{@link SuppressionSet}, no soul mode, and a chance roll that always returns
     * {@code 0.0} so any positive chance passes — production MUST install a random-backed roll.
     */
    public static final class Builder {

        private final UUID actor;
        private final int worldId;
        private final int triggerId;
        private final long nowTicks;
        private FactBuffer facts = new FactBuffer(0, 0, 0);
        private SuppressionSet suppression = new SuppressionSet();
        private DoubleSupplier chanceRoll = () -> 0.0;
        private UUID activeGem;
        private SoulLedger.Balance gemBalance;
        private Location location;

        private Builder(UUID actor, int worldId, int triggerId, long nowTicks) {
            this.actor = actor;
            this.worldId = worldId;
            this.triggerId = triggerId;
            this.nowTicks = nowTicks;
        }

        public Builder facts(FactBuffer facts) {
            this.facts = Objects.requireNonNull(facts, "facts");
            return this;
        }

        public Builder suppression(SuppressionSet suppression) {
            this.suppression = Objects.requireNonNull(suppression, "suppression");
            return this;
        }

        public Builder chanceRoll(DoubleSupplier chanceRoll) {
            this.chanceRoll = Objects.requireNonNull(chanceRoll, "chanceRoll");
            return this;
        }

        /** Put the activator in soul mode with the given gem and its balance proxy. */
        public Builder soulMode(UUID gemId, SoulLedger.Balance balance) {
            this.activeGem = Objects.requireNonNull(gemId, "gemId");
            this.gemBalance = Objects.requireNonNull(balance, "balance");
            return this;
        }

        /** The firing location for the protection gate (gate 2); {@code null} for a non-positional event. */
        public Builder location(Location location) {
            this.location = location;
            return this;
        }

        public Activation build() {
            return new Activation(this);
        }
    }
}
