package engine.run;

import engine.condition.FactBuffer;
import engine.condition.SubjectBody;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * The subject cursor (ADR-0076): the one re-bindable {@code %target.*%} subject an effect's per-target pass
 * and its {@code perTarget()} arguments read. One instance per worker thread, re-pointed at each body — a
 * UUID, a double and a reference write, so a 20-body AoE allocates nothing for the cursor itself.
 *
 * <p>It answers the two BODY-derived subject facts directly ({@link SubjectBody}); everything else is a
 * UUID-keyed store read the populator installed, which is what keeps the whole scope free of live entity
 * access. Both reads here were already made for this body, on this thread, by the selector's own
 * {@code ENEMIES}/{@code ALLIES} filter, so neither needs a region hop or a {@code Regions} guard.
 */
final class SubjectCursor implements SubjectBody, Iterable<LivingEntity> {

    private FactBuffer facts;
    private Player actor;
    private DoubleSupplier roll = () -> 0.0;
    private LivingEntity body;
    private List<LivingEntity> walk = List.of();
    // ONE draw per body per ABILITY, memoised here rather than taken per bind. That is what makes a filter and
    // its complement two rows of ONE roll — the property the shipped two-ability idiom cannot have, since its
    // arms roll independently and can both land or both miss. A linear scan over parallel arrays that live on
    // the thread-local cursor: AoE lists are tens of bodies at most, and the arrays are reused walk to walk, so
    // the steady state allocates nothing.
    private UUID[] drawnFor = new UUID[8];
    private double[] draws = new double[8];
    private int drawn;

    /** Arm for one ability's effect walk: whose facts, whose activation, and where {@code %target.roll%} draws. */
    void arm(FactBuffer facts, Player actor, DoubleSupplier roll) {
        this.facts = facts;
        this.actor = actor;
        this.roll = roll == null ? () -> 0.0 : roll;
        this.drawn = 0; // a new proc is a new set of draws
    }

    /** Point at one resolved target, binding the ONE draw this body carries for the whole ability. */
    void bind(LivingEntity target) {
        this.body = target;
        if (facts == null) {
            return;
        }
        UUID id = target == null ? null : target.getUniqueId();
        facts.bindSubject(id, this, id == null ? 0.0 : rollFor(id));
    }

    /** This body's draw for this ability, taken on first sight and reused by every later row. */
    private double rollFor(UUID id) {
        for (int i = 0; i < drawn; i++) {
            if (id.equals(drawnFor[i])) {
                return draws[i];
            }
        }
        if (drawn == drawnFor.length) {
            drawnFor = Arrays.copyOf(drawnFor, drawn * 2);
            draws = Arrays.copyOf(draws, drawn * 2);
        }
        drawnFor[drawn] = id;
        draws[drawn] = roll.getAsDouble();
        return draws[drawn++];
    }

    /** Unbind, so nothing downstream can read a stale body's facts. */
    void clear() {
        this.body = null;
        this.walk = List.of();
        if (facts != null) {
            facts.clearSubject();
        }
    }

    /**
     * A cursor-advancing view of {@code targets} for a kind that declares a {@code perTarget()} argument — it
     * REPLACES the iterator the effect's for-each would have allocated anyway, so an opted-in kind's profile is
     * one iterator, and every other kind's is byte-identical to before.
     */
    Iterable<LivingEntity> over(List<LivingEntity> targets) {
        this.walk = targets;
        return this;
    }

    @Override
    public Iterator<LivingEntity> iterator() {
        List<LivingEntity> targets = walk;
        return new Iterator<>() {
            private int index;

            @Override
            public boolean hasNext() {
                if (index < targets.size()) {
                    return true;
                }
                clear(); // the walk ended — a later effect must not inherit the last body
                return false;
            }

            @Override
            public LivingEntity next() {
                LivingEntity next = targets.get(index++);
                bind(next);
                return next;
            }
        };
    }

    @Override
    public String type() {
        return body == null ? "" : body.getType().name();
    }

    @Override
    public String relation() {
        return body == null ? "" : FactPopulator.relationOf(actor, body);
    }
}
