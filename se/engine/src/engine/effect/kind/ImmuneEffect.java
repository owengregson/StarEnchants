package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import java.util.Locale;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/**
 * {@code IMMUNE} — make the target immune to a damage cause for a duration (the EE {@code IMMUNE} effect,
 * § combat-flags). A combat-flag like {@code KNOCKBACK_CONTROL}: the proc writes a per-player timed flag
 * through the {@link Sink}, and a SEPARATE Bukkit event (future damage) reads it back and cancels matching
 * hits. {@code type} is sword / axe / projectile / potion (magic·poison·wither) / all. Targets the activator
 * by default (a self-protection). Player-only. {@link Affinity#CONTEXT_LOCAL}: an in-memory flag write.
 */
public final class ImmuneEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("IMMUNE")
            .param("type", D.enumOf("sword", "axe", "projectile", "potion", "all"))
            .param("duration", D.TICKS.def(100))
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Make the target player(s) immune to a damage cause (sword/axe/projectile/potion/all) for "
                    + "duration ticks.")
            .example("IMMUNE:potion:100")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int code = typeCode(ctx.str("type"));
        int duration = ctx.integer("duration");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player player) {
                sink.immune(player, code, duration);
            }
        }
    }

    /** Map the authored enum to the {@code ImmuneStore.Type} ordinal the {@code Sink} expects. */
    private static int typeCode(String type) {
        return switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
            case "axe" -> 1;
            case "projectile" -> 2;
            case "potion" -> 3;
            case "all" -> 4;
            default -> 0; // sword
        };
    }
}
