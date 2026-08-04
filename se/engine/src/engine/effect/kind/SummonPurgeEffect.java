package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.sink.SummonPurgeFilter;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/**
 * {@code SUMMON_PURGE} — clear the ground of other people's summons. {@code CONVERT_SUMMON} is the near-miss
 * and it is the exact inverse: the bell KEEPS every mob and flips it onto your side, while this removes only
 * what it can attribute to another player and leaves wild spawns alone.
 *
 * <p>Removal is a DESPAWN, not a kill — no drops, no experience, no death event — so a purge can never feed
 * the owner's on-death content or credit anyone a kill. An invincible summon (ADR-0052) survives it exactly
 * as it survives {@code DESPAWN} and {@code KILL}.
 */
public final class SummonPurgeEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SUMMON_PURGE")
            // Capped at CONVERT_SUMMON's ceiling: both walk getNearbyEntities, so both pay the same box scan.
            .param("radius", D.DOUBLE.range(1, 32).def(15), "how far the sweep reaches from the wearer")
            .param("filter", D.enumOf(SummonPurgeFilter.names()).def(SummonPurgeFilter.NOT_OWN_OR_ALLY_OR_OFFLINE),
                    "which owners are SPARED, weakest sweep last")
            // Two bursts because the despawn puff is layered: an absent handle means no burst, so an
            // unauthored slot costs nothing.
            .param("particle", D.particle().optional(), "burst left where each purged summon stood; omit for none")
            .param("particle-count", D.INT.min(0).def(1))
            .param("particle-spread", D.DOUBLE.min(0).def(0), "per-axis spread of the burst (0 = a point)")
            .param("extra-particle", D.particle().optional(), "second burst layered on the first; omit for none")
            .param("extra-particle-count", D.INT.min(0).def(1))
            .param("extra-particle-spread", D.DOUBLE.min(0).def(0))
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Despawn every summon within `radius` blocks of the wearer whose owner the `filter` does not "
                    + "spare, leaving the particle / extra-particle burst where each one stood. The filter is "
                    + "a ladder of exemptions: not-own spares only the wearer's summons, not-own-or-ally also "
                    + "spares an ONLINE ally's, and not-own-or-ally-or-offline additionally spares one whose "
                    + "owner has logged off (an abandoned summon is left to its own TTL). Only summons the "
                    + "engine can attribute to a player are touched — a wild mob, and a summon spawned with "
                    + "owner=none, are not summons anyone owns and are left alone. The removal is a DESPAWN: "
                    + "no drops, no experience and no death event, so nothing the owner hung on a death fires. "
                    + "An invincible summon survives, exactly as it survives DESPAWN and KILL. "
                    + "CONVERT_SUMMON is the inverse — it keeps the summons and flips them onto your side.")
            .example("{ SUMMON_PURGE: { radius: 15, filter: not-own-or-ally-or-offline, "
                    + "particle: LARGE_SMOKE, particle-count: 10, particle-spread: 0.3, "
                    + "extra-particle: SPELL_WITCH, extra-particle-count: 12, extra-particle-spread: 0.7 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double radius = ctx.dbl("radius");
        String filter = ctx.str("filter");
        // An absent HANDLE never interns, so -1 is unambiguously "no burst" — no id can collide with it.
        int particleId = ctx.args().has("particle") ? ctx.integer("particle") : -1;
        int particleCount = particleId < 0 ? 0 : ctx.integer("particle-count");
        double particleSpread = particleId < 0 ? 0 : ctx.dbl("particle-spread");
        int extraId = ctx.args().has("extra-particle") ? ctx.integer("extra-particle") : -1;
        int extraCount = extraId < 0 ? 0 : ctx.integer("extra-particle-count");
        double extraSpread = extraId < 0 ? 0 : ctx.dbl("extra-particle-spread");
        for (LivingEntity who : ctx.targets("who")) {
            if (who instanceof Player player) {
                sink.purgeSummons(player, radius, filter, particleId, particleCount, particleSpread,
                        extraId, extraCount, extraSpread);
            }
        }
    }
}
