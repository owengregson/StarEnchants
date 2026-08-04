package engine.interact;

import compile.model.Ability;
import engine.stores.ReboundStore;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.ToIntFunction;

/**
 * The rebound arbiter (PROC_REBOUND — Enchant Reflect): per-event scratch that decides which of the
 * ATTACKER's activating abilities the reflector steals, exactly as {@link SuppressionSet} and
 * {@link DamageFold} are per-event scratch owned by the single firing thread (§6). Not thread-safe.
 *
 * <p>Built only when the victim is a player with a grade armed, and attached ONLY to the attack-side
 * activation — on the defence walk it would claim the victim's own abilities.
 */
public final class ReboundPlan {

    /** Claimed ability ids, in claim order; the caller re-executes exactly these with roles swapped. */
    private int[] claimed = new int[4];
    private int size;

    private final ReboundStore rebounds;
    private final UUID reflector;
    /** Ability id → rarity-tier weight, precomputed per snapshot ({@code -1} = no tier, never rebounded). */
    private final ToIntFunction<Ability> tierOf;
    private final DoubleSupplier roll;

    public ReboundPlan(ReboundStore rebounds, UUID reflector, ToIntFunction<Ability> tierOf, DoubleSupplier roll) {
        this.rebounds = rebounds;
        this.reflector = reflector;
        this.tierOf = tierOf;
        this.roll = roll;
    }

    /**
     * Gate 9's decision for one activating ability: {@code true} VETOES it (the reflected enchant is not
     * applied to the reflector for this hit) and records it for the swapped re-execution.
     *
     * <p>Order is deliberate — band, then level, then the roll — so an enchant no armed grade answers for
     * never consumes a draw. The grade is resolved per ability because the precedence chain keys on the
     * INCOMING tier, which differs from one of the attacker's enchants to the next.
     */
    public boolean claim(Ability ability) {
        int tier = tierOf.applyAsInt(ability);
        if (tier < 0) {
            return false; // a source with no rarity tier (pets, reforges, masks) is outside the chain
        }
        ReboundStore.Rule rule = rebounds.strongestFor(reflector, tier);
        if (rule == null || ability.level() > rule.level()) {
            return false; // the matrix's level gate: rebound level must be >= the incoming enchant's level
        }
        if (!(roll.getAsDouble() < rule.chancePercent())) {
            return false;
        }
        record(ability.id());
        return true;
    }

    /** Whether anything was claimed — the guard the cold rebound branch and the commit read hang off. */
    public boolean claimedAny() {
        return size > 0;
    }

    /**
     * The claimed ability ids, in claim order, as the executor's candidate array. May repeat an id: an
     * ECHO_STRIKE second pass is a SECOND activation of the same ability and is rolled — and rebounded —
     * separately from the first.
     */
    public int[] claimed() {
        int[] exact = new int[size];
        System.arraycopy(claimed, 0, exact, 0, size);
        return exact;
    }

    private void record(int abilityId) {
        if (size == claimed.length) {
            int[] grown = new int[size * 2];
            System.arraycopy(claimed, 0, grown, 0, size);
            claimed = grown;
        }
        claimed[size++] = abilityId;
    }
}
