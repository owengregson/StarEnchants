package engine.effect.kind;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Drift guard for the ADR-0043 actor-origin declaration: the set of built-in kinds that flag
 * {@code actorOrigin()} is a deliberate, reviewed list. A new kind that anchors on the actor must consciously
 * join it (and read the captured snapshot, never the live — possibly remote — actor).
 */
class ActorOriginDeclarationTest {

    @Test
    void exactlyTheActorAnchoredKindsFlagActorOrigin() {
        Set<String> flagged = new TreeSet<>();
        BuiltinEffects.registry().kinds().forEach(k -> {
            if (k.spec().needsActorOrigin()) {
                flagged.add(k.head());
            }
        });
        assertEquals(
                new TreeSet<>(Set.of("PARTICLE_RING", "PARTICLE_LINE", "WALKER", "SPAWN_ENTITY",
                        "TELEPORT_BEHIND", "TELEPORT", "VELOCITY")),
                flagged);
    }
}
