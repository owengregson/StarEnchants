package engine.pipeline;

import engine.condition.FactBuffer;
import engine.interact.SuppressionSet;
import java.util.Objects;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import org.bukkit.Location;

/**
 * The per-event context one ability is evaluated against by the {@link ActivationPipeline}
 * (docs/architecture.md §3.3); a trigger listener builds one per Bukkit event. Everything here is an
 * immutable per-event input or a reference to scratch the firing thread owns ({@link FactBuffer},
 * {@link SuppressionSet}); the long-lived stores (cooldowns, souls) belong to the pipeline.
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
    private final Location location;
    private final int targetBucket;

    private Activation(Builder b) {
        this.actor = Objects.requireNonNull(b.actor, "actor");
        this.worldId = b.worldId;
        this.triggerId = b.triggerId;
        this.nowTicks = b.nowTicks;
        this.facts = b.facts;
        this.suppression = b.suppression;
        this.chanceRoll = b.chanceRoll;
        this.activeGem = b.activeGem;
        this.location = b.location;
        this.targetBucket = b.targetBucket;
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

    /** A non-null marker iff the activator is in soul mode (gate 10 + REMOVE_SOULS); {@code null} otherwise. */
    public UUID activeGem() {
        return activeGem;
    }

    /**
     * The firing location (gate 2), snapshotted on the firing thread (its own region on Folia) so the
     * protection {@code Guard} can query the owning region safely. {@code null} (non-positional) reads as "allow".
     */
    public Location location() {
        return location;
    }

    /**
     * The cooldown target bucket — {@code 1} when this activation's other combat party is a player, {@code 0}
     * for a mob or a non-combat activation. Folded into the cooldown key (gates 6 + 11) so an ability cools
     * down independently per target kind: proccing it on a mob and on a player are two separate cooldown routes.
     */
    public int targetBucket() {
        return targetBucket;
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
        private Location location;
        private int targetBucket;

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

        /** Mark the activator as in soul mode (the cross-gem pool is the spend authority — gate 10 / §D). */
        public Builder soulMode(UUID marker) {
            this.activeGem = Objects.requireNonNull(marker, "marker");
            return this;
        }

        /** The firing location for the protection gate (gate 2); {@code null} for a non-positional event. */
        public Builder location(Location location) {
            this.location = location;
            return this;
        }

        /** The cooldown target bucket (1 = player other-party, 0 = mob / non-combat) — gates 6 + 11. */
        public Builder targetBucket(int targetBucket) {
            this.targetBucket = targetBucket;
            return this;
        }

        public Activation build() {
            return new Activation(this);
        }
    }
}
