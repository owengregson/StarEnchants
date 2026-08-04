package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.BlockFieldProfile;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import platform.caps.Regions;
import schema.spec.D;

/**
 * {@code FALLING_BLOCK} — rain a (2r+1)² grid of falling blocks {@code height} blocks above each target. The
 * blocks never drop an item or hurt, and are removed after {@code ttl} if they never land. When a block LANDS,
 * the {@link engine.sink.FallingBlockCasts}/landing listener fires the actor's {@code IMPACT}-triggered abilities
 * on whatever it landed on — so the impact is fully abstractable (any effects hang off {@code trigger: IMPACT}).
 * {@code carry} is forwarded to the impact as {@code %damage%} (set {@code carry: "%damage%"} to carry this hit).
 * Druid's Terrablender rains grass that deals 1.5× the hit + strips Speed on impact.
 *
 * <p>The block-field profile ({@code layers-*}, {@code layer-step-*}, {@code density}, {@code material2..4},
 * {@code damage-percent}/{@code health-cap}, {@code rehit-*}, {@code kill-material}) turns that one grid into a
 * multi-layer area-denial STORM — Dimensional Traveler's shift. Every knob is inert at its default, so a field
 * that authors none of them rains exactly the grid it always did.
 */
public final class FallingBlockEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("FALLING_BLOCK")
            .param("material", D.material())
            .param("material2", D.material().optional())
            .param("material3", D.material().optional())
            .param("material4", D.material().optional())
            .param("radius", D.INT.range(0, 4).def(1))
            // Widened past the old 12: a storm's origin sits high above its target and its top layer higher
            // still (the measured +10 origin plus (12..19) x 3 = 67), and out-of-world layers just do not rain.
            .param("height", D.INT.range(0, 64).def(4))
            .param("ttl", D.TICKS.def(40))
            .param("carry", D.DOUBLE.def(0))
            .param("layers-min", D.INT.range(1, 8).def(1), "fewest grids stacked above the target")
            .param("layers-max", D.INT.range(1, 8).def(1), "most grids stacked above the target")
            .param("layer-step-min", D.INT.range(0, 24).def(0), "fewest blocks one layer rises per layer index")
            .param("layer-step-max", D.INT.range(0, 24).def(0), "most blocks one layer rises per layer index")
            .param("density", D.DOUBLE.range(0, 100).def(100),
                    "percent of grid positions that actually rain, drawn per position per layer")
            .param("damage-percent", D.DOUBLE.min(0).def(0),
                    "percent of the target's (capped) max health added to carry — a victim-scaled impact")
            .param("health-cap", D.DOUBLE.min(0).def(0),
                    "ceiling on the max health damage-percent reads; 0 = uncapped")
            .param("rehit-max", D.INT.min(0).def(0),
                    "most impacts one victim can take per rehit-window, shared across every wearer; 0 = uncapped")
            .param("rehit-window", D.TICKS.def(200), "length of that fixed bucket, anchored at the first impact")
            .param("kill-material", D.material().optional(),
                    "a block falling through this material dies without ever landing — the field's counterplay")
            .target("who", T.VICTIM)
            .affinity(Affinity.REGION)
            .doc("Spawn a (2*radius+1)² grid of falling blocks `height` blocks above each target (removed after "
                    + "`ttl` if they never land). A landing block fires the actor's IMPACT abilities on what it "
                    + "hit; `carry` is forwarded to that impact as %damage% (set carry: \"%damage%\"). "
                    + "The block-field profile turns the grid into a storm and is entirely opt-in: layers-min/max "
                    + "stack that many grids, each layer index rising by its own draw from layer-step-min..max "
                    + "(so layer 0 is always `height`, and a layer above the world simply does not rain); density "
                    + "below 100 rains only that percent of positions, drawn fresh per position per layer, so a "
                    + "re-cast field never falls in the same holes; material2/3/4 give the storm a palette, drawn "
                    + "per block. damage-percent adds that percent of the target's max health — capped at "
                    + "health-cap — to `carry`, so one field hurts a 20-heart player and a boss proportionally. "
                    + "rehit-max/rehit-window cap how many impacts ONE victim can take in a fixed window shared "
                    + "across every wearer raining on them (the field's lethality ceiling), and kill-material "
                    + "names a block that kills a falling block mid-flight, so standing in it is real counterplay.")
            .example("{ FALLING_BLOCK: { material: END_STONE, material2: NETHERRACK, radius: 4, height: 10, "
                    + "layers-min: 3, layers-max: 4, layer-step-min: 12, layer-step-max: 19, density: 50, "
                    + "damage-percent: 15, health-cap: 44, rehit-max: 4, rehit-window: 200, "
                    + "kill-material: COBWEB, ttl: 100, who: \"@Aoe{r=25, filter=ENEMIES}\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        List<Integer> palette = palette(ctx);
        int ttl = ctx.integer("ttl");
        double carry = ctx.dbl("carry");
        double damagePercent = ctx.dbl("damage-percent");
        double healthCap = ctx.dbl("health-cap");
        BlockFieldProfile profile = new BlockFieldProfile(
                ctx.integer("radius"), ctx.integer("height"),
                ctx.integer("layers-min"), ctx.integer("layers-max"),
                ctx.integer("layer-step-min"), ctx.integer("layer-step-max"),
                ctx.dbl("density"),
                ctx.integer("rehit-max"), ctx.integer("rehit-window"),
                // An absent HANDLE never interns, so -1 is unambiguously "no counterplay material".
                ctx.args().has("kill-material") ? ctx.integer("kill-material") : -1);
        UUID owner = ctx.actor() == null ? null : ctx.actor().getUniqueId();
        for (LivingEntity who : ctx.targets("who")) {
            UUID target = who.getUniqueId(); // the field is aimed at THIS entity — its IMPACT lands only on it
            Location base;
            double carried = carry;
            try {
                base = who.getLocation(); // T.VICTIM, but @Attacker on a DEFENSE trigger can be a cross-region shooter (ADR-0043)
                if (damagePercent > 0) {
                    // Read in the SAME guarded read as the position, because a block carries ONE number to its
                    // landing and a victim's max health cannot meaningfully move during the fall.
                    carried += damagePercent / 100.0 * cappedMaxHealth(who, healthCap);
                }
            } catch (RuntimeException unreadable) {
                Regions.swallowed("FallingBlockEffect.target", unreadable);
                continue;
            }
            if (base.getWorld() == null) {
                continue;
            }
            sink.fallingBlockField(base, palette, profile, ttl, owner, target, carried);
        }
    }

    /** The rain palette in authored order: [material, material2.., material3.., material4..], present ones only. */
    private static List<Integer> palette(EffectCtx ctx) {
        List<Integer> out = new ArrayList<>(4);
        out.add(ctx.integer("material"));
        if (ctx.args().has("material2")) {
            out.add(ctx.integer("material2"));
        }
        if (ctx.args().has("material3")) {
            out.add(ctx.integer("material3"));
        }
        if (ctx.args().has("material4")) {
            out.add(ctx.integer("material4"));
        }
        return List.copyOf(out); // the intent is deferred: hand the sink a carrier nothing can mutate later (§3.6)
    }

    /** The target's max health, capped at {@code cap} ({@code cap <= 0} = uncapped). */
    @SuppressWarnings("deprecation") // getMaxHealth(): the one accessor stable from the 1.8 floor through the 1.21.3 Attribute flip
    private static double cappedMaxHealth(LivingEntity who, double cap) {
        double max = who.getMaxHealth();
        return cap > 0 ? Math.min(max, cap) : max;
    }
}
