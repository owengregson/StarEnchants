package engine.run;

import compile.model.cond.NumExpr;
import compile.model.cond.NumExprMap;
import engine.condition.FactBuffer;
import engine.condition.NumExprEval;
import engine.effect.EffectCtx;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.Args;

/**
 * The concrete {@link EffectCtx} the {@link AbilityExecutor} builds per effect (docs/architecture.md §3.5).
 *
 * <p>A numeric argument may be a compiled {@link NumExpr} over {@code %variables%}:
 * {@link #dbl}/{@link #integer}/{@link #lng} evaluate it against the {@link FactBuffer} on read (so
 * {@code DAMAGE_MOD:attack:add:%combo% * 10} scales per hit); a constant reads with no work. {@code facts}
 * is {@code null} only on the lifecycle path, where an expression has no combat facts and evaluates to 0.
 */
final class RuntimeEffectCtx implements EffectCtx {

    private final Args args;
    private final ActivationContext context;
    private final Map<String, List<LivingEntity>> targetsBySlot;
    private final Map<String, List<Location>> locationsBySlot;
    private final int level;
    private final int sourceDefId;
    private final int sourceGroup;
    private final int cooldownScope;
    private final int cooldownTicks;
    private final UUID activeGem;
    private final FactBuffer facts;
    private final ActorOrigin origin;

    RuntimeEffectCtx(Args args, ActivationContext context,
                     Map<String, List<LivingEntity>> targetsBySlot,
                     Map<String, List<Location>> locationsBySlot, int level, int sourceDefId, int sourceGroup,
                     int cooldownScope, int cooldownTicks,
                     UUID activeGem, FactBuffer facts, ActorOrigin origin) {
        this.cooldownScope = cooldownScope;
        this.cooldownTicks = cooldownTicks;
        this.args = args;
        this.context = context;
        this.targetsBySlot = targetsBySlot;
        this.locationsBySlot = locationsBySlot;
        this.level = level;
        this.sourceDefId = sourceDefId;
        this.sourceGroup = sourceGroup;
        this.activeGem = activeGem;
        this.facts = facts;
        this.origin = origin;
    }

    @Override
    public double dbl(String name) {
        Object value = args.opt(name).orElse(null);
        if (value instanceof NumExpr expr) {
            return finite(facts == null ? 0.0 : NumExprEval.eval(expr, facts));
        }
        return args.dbl(name);
    }

    @Override
    public int integer(String name) {
        Object value = args.opt(name).orElse(null);
        if (value instanceof NumExpr expr) {
            return (int) Math.round(finite(facts == null ? 0.0 : NumExprEval.eval(expr, facts)));
        }
        return args.integer(name);
    }

    @Override
    public Map<String, Double> numbers(String name) {
        Object value = args.opt(name).orElse(null);
        if (!(value instanceof NumExprMap map) || map.isEmpty()) {
            return Map.of(); // absent or unbound: the shared empty map, so the common case allocates nothing
        }
        Map<String, Double> out = new LinkedHashMap<>();
        map.entries().forEach((binding, expr) ->
                out.put(binding, finite(facts == null ? 0.0 : NumExprEval.eval(expr, facts))));
        return out;
    }

    @Override
    public long lng(String name) {
        Object value = args.opt(name).orElse(null);
        if (value instanceof NumExpr expr) {
            return Math.round(finite(facts == null ? 0.0 : NumExprEval.eval(expr, facts)));
        }
        return args.lng(name);
    }

    /** Sanitize an evaluated expression: a non-finite result (NaN/∞ from a missing var) degrades to 0. */
    private static double finite(double v) {
        return Double.isFinite(v) ? v : 0.0;
    }

    @Override
    public boolean bool(String name) {
        return args.bool(name);
    }

    @Override
    public String str(String name) {
        return args.str(name);
    }

    @Override
    public Args args() {
        return args;
    }

    @Override
    public Player actor() {
        return context.actor();
    }

    @Override
    public LivingEntity victim() {
        return context.victim();
    }

    @Override
    public LivingEntity attacker() {
        return context.attacker();
    }

    @Override
    public Location location() {
        return context.location();
    }

    @Override
    public Location actorOrigin() {
        return origin == null || !origin.present() ? null : origin.feet();
    }

    @Override
    public Location actorOriginEye() {
        return origin == null || !origin.present() ? null : origin.eye();
    }

    @Override
    public Iterable<LivingEntity> targets(String selectorName) {
        return targetsBySlot.getOrDefault(selectorName, List.of());
    }

    @Override
    public Iterable<Location> targetLocations(String selectorName) {
        return locationsBySlot.getOrDefault(selectorName, List.of());
    }

    @Override
    public int level() {
        return level;
    }

    @Override
    public int sourceDefId() {
        return sourceDefId;
    }

    @Override
    public int sourceGroup() {
        return sourceGroup;
    }

    @Override
    public int cooldownScope() {
        return cooldownScope;
    }

    @Override
    public int cooldownTicks() {
        return cooldownTicks;
    }

    @Override
    public UUID activeGem() {
        return activeGem;
    }
}
