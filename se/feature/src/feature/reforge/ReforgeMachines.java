package feature.reforge;

import compile.load.ContentHolder;
import compile.load.ReforgeDef;
import compile.model.Ability;
import compile.model.CompiledEffect;
import compile.model.Snapshot;
import engine.effect.kind.GravityWellEffect;
import engine.effect.kind.GrappleEffect;
import engine.effect.kind.JavelinEffect;
import engine.effect.kind.SwapPositionEffect;
import java.util.Objects;
import java.util.function.BiConsumer;
import org.bukkit.entity.Player;

/**
 * The reforge machine dispatcher (ADR-0071): the seam Plan A's reforge runner calls at the point it observes an
 * ACTIVATED {@code UseAttempt} for a reforge def (the {@code PetService.digHomeEffect} walk point). Because the
 * four movement/space heads (GRAVITY_WELL, GRAPPLE, SWAP_POSITION, JAVELIN) are service-owned MARKERS — their
 * effect {@code run} bodies emit nothing — the machines only fire from here: this walks the def's USE abilities'
 * compiled effects against the CURRENT snapshot (the marker's numbers live in its args), matches each
 * {@code effect.head()} against the kinds' public {@code HEAD} constants and routes it to the owning service's
 * {@code start(actor, effect)}. Deliberately NOT a global {@code ActivationListener}: that chain fires for every
 * activation and would need SourceKind filtering in shared plumbing — the runner-side call keeps "adding a
 * feature is local" intact (the pets precedent). A {@link BiConsumer} so Plan A may take it as either the class
 * type or the bare functional interface.
 */
public final class ReforgeMachines implements BiConsumer<Player, ReforgeDef> {

    private final ContentHolder content;
    private final GravityWellService gravityWell;
    private final GrappleService grapple;
    private final CastlingService castling;
    private final JavelinService javelin;

    public ReforgeMachines(ContentHolder content, GravityWellService gravityWell, GrappleService grapple,
                           CastlingService castling, JavelinService javelin) {
        this.content = Objects.requireNonNull(content, "content");
        this.gravityWell = Objects.requireNonNull(gravityWell, "gravityWell");
        this.grapple = Objects.requireNonNull(grapple, "grapple");
        this.castling = Objects.requireNonNull(castling, "castling");
        this.javelin = Objects.requireNonNull(javelin, "javelin");
    }

    /** The activation-success hook: route every service-owned marker in {@code def}'s USE abilities to its machine. */
    public void onActivated(Player actor, ReforgeDef def) {
        Snapshot snapshot = content.snapshot();
        for (String key : def.useStableKeys()) {
            Ability ability = snapshot.byStableKey(key);
            if (ability == null) {
                continue;
            }
            for (CompiledEffect effect : ability.effects()) {
                route(actor, effect);
            }
        }
    }

    private void route(Player actor, CompiledEffect effect) {
        String head = effect.head();
        if (GravityWellEffect.HEAD.equals(head)) {
            gravityWell.start(actor, effect);
        } else if (GrappleEffect.HEAD.equals(head)) {
            grapple.start(actor, effect);
        } else if (SwapPositionEffect.HEAD.equals(head)) {
            castling.start(actor, effect);
        } else if (JavelinEffect.HEAD.equals(head)) {
            javelin.start(actor, effect);
        }
        // BLINK is a real kind (its intent runs in-engine) and every non-reforge head is ignored here.
    }

    @Override
    public void accept(Player actor, ReforgeDef def) {
        onActivated(actor, def);
    }
}
