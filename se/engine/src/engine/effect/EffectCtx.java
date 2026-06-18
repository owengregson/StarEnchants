package engine.effect;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.Args;

/**
 * The read-only context one effect activation runs against (docs/architecture.md
 * §3.5, §7). It exposes the effect's already-typed arguments, the activation's
 * actors, and its pre-resolved selector targets — with <em>no parsing</em> on the
 * hot path. An effect reads facts from here and emits results through the
 * {@link engine.sink.Sink}; it never reads a live cross-region entity itself (§3.4),
 * so everything reachable here is either the firing-thread actor or a snapshot-safe
 * value.
 */
public interface EffectCtx {

    // ── Typed arguments (pre-validated; read by name, no parsing) ──

    double dbl(String name);

    int integer(String name);

    long lng(String name);

    boolean bool(String name);

    String str(String name);

    /** The full typed argument bag, for effects that iterate or forward args. */
    Args args();

    // ── Activation actors ──

    /** The player whose ability fired. */
    Player actor();

    /** The combat victim, or {@code null} for non-combat activations. */
    LivingEntity victim();

    /** The relevant block/area location (e.g. an AoE centre), or {@code null}. */
    Location location();

    // ── Pre-resolved targets ──

    /**
     * The living entities resolved for the named target slot (declared via
     * {@code EffectSpec.target}). Empty if the selector matched nothing — never null.
     */
    Iterable<LivingEntity> targets(String selectorName);

    /** The activating ability's level (enchants; {@code 0} for other sources). */
    int level();

    /**
     * The activator's active soul-gem id, or {@code null} when they are not in soul mode (REMOVE_SOULS).
     * Souls bind to the activator, so this is the actor's gem — not a target's.
     */
    UUID activeGem();
}
