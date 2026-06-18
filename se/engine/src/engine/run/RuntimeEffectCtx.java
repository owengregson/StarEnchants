package engine.run;

import engine.effect.EffectCtx;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.Args;

/**
 * The concrete {@link EffectCtx} the {@link AbilityExecutor} builds per effect: the effect's typed
 * {@link Args}, the activation's actors (from the {@link ActivationContext}), the selector targets
 * already resolved into each declared slot, and the ability level (docs/architecture.md §3.5, §7).
 * An effect reads facts from here and emits results through the {@code Sink}; there is no parsing and
 * no entity touch on the hot path.
 */
final class RuntimeEffectCtx implements EffectCtx {

    private final Args args;
    private final ActivationContext context;
    private final Map<String, List<LivingEntity>> targetsBySlot;
    private final int level;
    private final UUID activeGem;

    RuntimeEffectCtx(Args args, ActivationContext context,
                     Map<String, List<LivingEntity>> targetsBySlot, int level, UUID activeGem) {
        this.args = args;
        this.context = context;
        this.targetsBySlot = targetsBySlot;
        this.level = level;
        this.activeGem = activeGem;
    }

    @Override
    public double dbl(String name) {
        return args.dbl(name);
    }

    @Override
    public int integer(String name) {
        return args.integer(name);
    }

    @Override
    public long lng(String name) {
        return args.lng(name);
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
    public Location location() {
        return context.location();
    }

    @Override
    public Iterable<LivingEntity> targets(String selectorName) {
        return targetsBySlot.getOrDefault(selectorName, List.of());
    }

    @Override
    public int level() {
        return level;
    }

    @Override
    public UUID activeGem() {
        return activeGem;
    }
}
