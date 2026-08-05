package engine.sink;

import org.bukkit.entity.Entity;

/**
 * The sink's view of a summon's payload — a seam because firing a trigger is the feature layer's business and
 * no engine class may reach it. Only the periodic phase rides it (the event-driven phases already live in
 * listeners); it is called on the summon's OWN thread. Wired at the composition root.
 */
@FunctionalInterface
public interface SummonPayloads {

    /** The inert default for non-root construction sites (tests, tester suites): no payload ever runs. */
    SummonPayloads NONE = (summon, flags) -> { };

    /** Run {@code summon}'s owner's {@code SUMMON_PAYLOAD} abilities over the box {@code flags} describes. */
    void fire(Entity summon, SummonFlags flags);

    /**
     * The ability ids {@code ownerId}'s {@code SUMMON_PAYLOAD} walk resolves to RIGHT NOW, to be pinned onto a
     * summon being spawned; {@code null} when there is nothing to pin. Reading worn state is the feature
     * layer's business, which is why it rides this seam rather than the sink.
     */
    default int[] payloadCandidates(java.util.UUID ownerId) {
        return null;
    }
}
