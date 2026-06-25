package engine.run;

import engine.selector.SelectorCtx;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.Args;

/** The concrete {@link SelectorCtx} per effect; world access routes through {@link AreaScan} so selector kinds stay pure. */
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

    @Override
    public Player playerByName(String name) {
        return areaScan.playerByName(name);
    }

    @Override
    public LivingEntity entityInSight(double maxDistance) {
        // Raytrace from the activator, on its own firing region thread — region-correct on Folia.
        return context.actor() == null ? null : areaScan.entityInSight(context.actor(), maxDistance);
    }

    @Override
    public Location targetBlock(double maxDistance) {
        return context.actor() == null ? null : areaScan.targetBlock(context.actor(), maxDistance);
    }

    @Override
    public java.util.List<Location> vein(Location start, int limit) {
        return areaScan.vein(start, limit);
    }
}
