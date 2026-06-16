package engine.run;

import engine.selector.SelectorCtx;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.Args;

/**
 * The concrete {@link SelectorCtx} the {@link AbilityExecutor} builds per effect: the activation's
 * actors (from the {@link ActivationContext}) plus the selector's typed {@link Args} and the injected
 * {@link AreaScan} (docs/architecture.md §3.5). A selector reads facts from here and never touches a
 * {@code World} itself — area scans go through {@link #nearbyLiving}, so the kinds stay pure.
 */
final class RuntimeSelectorCtx implements SelectorCtx {

    private final ActivationContext context;
    private final Args args;
    private final AreaScan areaScan;

    RuntimeSelectorCtx(ActivationContext context, Args args, AreaScan areaScan) {
        this.context = context;
        this.args = args;
        this.areaScan = areaScan;
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
    public Args args() {
        return args;
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
    public Iterable<LivingEntity> nearbyLiving(Location center, double radius) {
        return areaScan.nearbyLiving(center, radius);
    }
}
