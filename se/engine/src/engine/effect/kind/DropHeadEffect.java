package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/** {@code DROP_HEAD} — drop a selected player's head now or mark it for their next death. */
public final class DropHeadEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("DROP_HEAD")
            .param("defer", D.BOOL.def(false))
            .param("channel", D.STRING.def("default"))
            .affinity(Affinity.TARGET_ENTITY)
            .target("who", "VICTIM")
            .doc("Drop each selected player's skinned head, or mark one per channel for their next death when defer is true.")
            .example("{ DROP_HEAD: { defer: true, channel: headless, who: \"@Victim\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        boolean defer = ctx.bool("defer");
        String channel = ctx.str("channel");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player player) {
                if (defer) {
                    sink.markHeadDrop(player, channel);
                } else {
                    sink.dropHead(player, ctx.actor());
                }
            }
        }
    }
}
