package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import java.util.List;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/**
 * {@code SUMMON_REBIND} — replace one of the activator's OWN summons with a fresh, fully-statted one just
 * above it: the old body is removed silently (no death event, no drops) and the replacement spawns with its
 * self-destruct window restarted. {@code CONVERT_SUMMON} rebinds ownership in place — same health, same
 * tier, same name — so a theft that is meant to read as an upgrade needs this instead.
 *
 * <p>The loadout params are {@code GUARD}'s, so an upgraded summon and a summoned one are styled the same way
 * and neither grows a private stats vocabulary.
 */
public final class SummonRebindEffect implements EffectKind {

    public static final EffectSpec SPEC = EffectSpec.of("SUMMON_REBIND")
            .param("type", D.entityType())
            .param("ttl", D.TICKS.def(600))
            .param("name", D.STRING.def(""), "custom name shown above the replacement; {OWNER} fills in the summoner")
            .param("health", D.DOUBLE.min(0).def(0), "starting (and maximum) health; 0 keeps the vanilla one")
            .param("speed", D.DOUBLE.min(0).def(0), "movement-speed multiplier; 0 keeps the vanilla one")
            .param("effects", D.potionEffects().def(""), "potion effects held for the replacement's whole life")
            .param("rise", D.DOUBLE.range(0, 8).def(2), "blocks above the old body to place the replacement")
            .param("steal", D.BOOL.def(false),
                    "also take summons owned by SOMEONE ELSE (a summon it must still be — never a wild mob)")
            .param("steal-message", D.STRING.def(""),
                    "steal only: broadcast near the replacement; {FROM} is the robbed owner, {OWNER} the thief")
            .param("steal-radius", D.DOUBLE.min(0).max(64).def(24),
                    "how far the steal-message carries")
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Replace each target summon the activator OWNS with a fresh one of type, rise blocks above "
                    + "it: the old body is removed silently (no death, no drops, no kill credit) and the "
                    + "replacement spawns at full health with a restarted ttl. health, speed, name and "
                    + "effects are GUARD's loadout params. A summon the activator does not own is skipped "
                    + "unless steal is set, which widens the precondition from 'mine' to 'somebody's' — the "
                    + "target must still be a tracked summon, so a farmed wild mob can never be turned into "
                    + "a free top-tier guardian. steal-message is the only place both names exist at once, "
                    + "which is why the broadcast rides the effect instead of a MESSAGE line. "
                    + "CONVERT_SUMMON rebinds ownership in place; this replaces the body.")
            .example("{ SUMMON_REBIND: { type: IRON_GOLEM, ttl: 600, health: 90, "
                    + "name: \"&b&l{OWNER}'s Guardian\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        if (ctx.actor() == null) {
            return; // ownership is the whole gate: with no activator nothing is owned
        }
        int type = ctx.integer("type");
        int ttl = ctx.integer("ttl");
        String name = ctx.str("name");
        double health = ctx.dbl("health");
        double speed = ctx.dbl("speed");
        List<Integer> effects = ctx.ids("effects");
        double rise = ctx.dbl("rise");
        boolean steal = ctx.bool("steal");
        String stealMessage = steal ? ctx.str("steal-message") : "";
        double stealRadius = steal ? ctx.dbl("steal-radius") : 0;
        for (LivingEntity target : ctx.targets("who")) {
            sink.rebindSummon(target, ctx.actor(), type, ttl, name, health, speed, effects, rise,
                    steal, stealMessage, stealRadius);
        }
    }
}
