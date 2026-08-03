package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/**
 * {@code HEAD_TROPHY} — arm a trophy on the target: their NEXT death from any cause adds a skull of
 * themselves to the drops, then the mark is spent. The arm is not a kill, so the head is collected by
 * whoever happens to land the eventual death.
 *
 * <p>Two primitives are missing without it: no drop kind carries a skull OWNER, and no trigger runs on the
 * victim's death for state an ATTACKER planted (DEATH walks the dying player's own gear). The templates are
 * carried here rather than resolved here for the same reason — the killer, the place and the weapon are
 * facts of the death, not of the hit that armed it.
 */
public final class HeadTrophyEffect implements EffectKind {

    public static final EffectSpec SPEC = EffectSpec.of("HEAD_TROPHY")
            .param("name", D.STRING.def(""), "display-name template for the dropped skull")
            .param("lore", D.STRING.def(""), "lore template; '|' separates lines")
            .target("who", T.VICTIM)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Arm a head trophy on the target: on their next death from ANY cause a player head owned by "
                    + "them joins the drops, named and lored from these templates, and the mark clears. "
                    + "Tokens resolve at the death: {VICTIM}, {KILLER}, {MONTH}, {DAY}, {YEAR}, {X}, {Y}, "
                    + "{Z}, {ITEM} (the killer's held item, else Fists). Lore lines are separated by '|'. A "
                    + "killer-less death drops the bare head with no lore, since every lore token would be "
                    + "empty. Player targets only.")
            .example("{ HEAD_TROPHY: { name: \"&fSkull of {VICTIM}\", "
                    + "lore: \"&7Defeated by &f{KILLER}|&f{MONTH} {DAY}, {YEAR}\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        String name = ctx.str("name");
        String lore = ctx.str("lore");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player player) {
                sink.armHeadTrophy(player, name, lore);
            }
        }
    }
}
