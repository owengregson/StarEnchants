package feature.summon;

import engine.selector.kind.Targets;
import engine.sink.GuardianCasts;
import engine.sink.PetSummons;
import engine.sink.SummonFlags;
import engine.sink.SummonPayloads;
import feature.trigger.TriggerDispatch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Runs a summon's payload: the shared target box every phase (detonate / death / periodic) fires through.
 * Owner resolution is {@code GuardianHurtListener}'s — by UUID, never a cross-region dereference — so a
 * payload with no owner, or an offline one, simply does not run. The box scan reads only the summon and its
 * neighbours, which is why every caller must already be on the summon's own region thread.
 */
public final class SummonPayloadService implements SummonPayloads {

    private final TriggerDispatch dispatch;

    public SummonPayloadService(TriggerDispatch dispatch) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
    }

    @Override
    public void fire(Entity summon, SummonFlags flags) {
        if (summon == null || flags == null || !flags.payloadArmed()) {
            return;
        }
        UUID ownerId = GuardianCasts.owner(summon.getUniqueId());
        if (ownerId == null) {
            return; // an unowned summon has nobody's abilities to run (a payload wants owner=activator)
        }
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null) {
            dispatch(owner, summon, select(summon, owner, flags), PetSummons.pinnedPayload(summon.getUniqueId()));
        }
    }

    @Override
    public int[] payloadCandidates(UUID ownerId) {
        return ownerId == null ? null : dispatch.payloadCandidatesNow(ownerId);
    }

    /**
     * One activation per target, so the payload's damage/ignite/knockback stay ordinary authored effects.
     *
     * <p>{@code pinned} is the spawning activation's own answer, kept because a death-triggered charge goes
     * off for an owner who has already dropped the armour that armed it — the live walk would find nothing
     * and the blast, whose vanilla explosion the detonate phase already cancelled, would land as a dud. With
     * no pin (an older summon, a reload since, a phase nobody pinned) the live read is still the right one.
     */
    void dispatch(Player owner, Entity summon, List<LivingEntity> targets, int[] pinned) {
        for (int i = 0; i < targets.size(); i++) {
            if (pinned != null) {
                dispatch.fireSummonPayloadPinned(owner, summon, targets.get(i), pinned);
            } else {
                dispatch.fireSummonPayload(owner, summon, targets.get(i));
            }
        }
    }

    /**
     * The payload's targets: everything living in the {@code payload-radius} x {@code payload-height} box
     * around the summon that the shared selector {@code filter} admits, capped nearest-first. The owner is
     * never their own target (the {@code @Aoe} "except the activator" rule) and neither is a spectator.
     */
    static List<LivingEntity> select(Entity summon, Player owner, SummonFlags flags) {
        double rx = flags.payloadRadius();
        double ry = flags.payloadHeight() > 0 ? flags.payloadHeight() : rx; // 0 = a cube on the radius
        Targets.Match filter = Targets.of(flags.payloadFilter());
        List<LivingEntity> matched = new ArrayList<>();
        for (Entity nearby : summon.getNearbyEntities(rx, ry, rx)) {
            if (nearby instanceof LivingEntity living && !living.equals(owner) && !living.isDead()
                    && !spectator(living) && filter.accepts(owner, living)) {
                matched.add(living);
            }
        }
        int cap = flags.payloadMaxTargets();
        if (cap > 0 && matched.size() > cap) {
            Location center = summon.getLocation();
            matched.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(center)));
            return new ArrayList<>(matched.subList(0, cap));
        }
        return matched;
    }

    /** A spectator is not in the world for gameplay purposes; the enum constant is stable across the range. */
    private static boolean spectator(LivingEntity entity) {
        return entity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR;
    }
}
