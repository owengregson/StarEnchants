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
 * {@code PERIODIC_DAMAGE} — an actor-attributed burn: {@code amount} raw half-hearts every {@code period}
 * ticks for {@code duration}, with an optional {@code feedback} line and {@code tick-*} cue each pulse.
 * {@code replace} names the vanilla potion DoTs the burn CONVERTS: only their damage ticks are cancelled for
 * the window, so the burn is the single damage clock while the named effect stays on the victim, visible.
 *
 * <p>FREEZE's {@code dot} is the only other periodic hurt and it is frost-themed by construction (a root plus
 * the powder-snow visual); nothing there can carry a fire or wither identity, and its window is a root's
 * window, not a burn's.
 */
public final class PeriodicDamageEffect implements EffectKind {

    public static final EffectSpec SPEC = EffectSpec.of("PERIODIC_DAMAGE")
            .param("amount", D.DOUBLE.min(0), "raw pre-armor half-hearts per pulse (never attack-scaled)")
            .param("period", D.TICKS.def(20))
            .param("duration", D.TICKS.def(100))
            .param("replace", D.potionEffects().def(""),
                    "vanilla DoTs this burn converts: their damage ticks are cancelled, the effect stays visible")
            .param("feedback", D.STRING.def(""), "line sent to a player target on every pulse")
            // Per-pulse cosmetics: absent handles mean silence/no burst, so an unauthored burn is unchanged.
            .param("tick-sound", D.sound().optional(), "cue played at the target on every pulse; omit for silence")
            .param("tick-volume", D.DOUBLE.min(0).def(1))
            .param("tick-pitch", D.DOUBLE.min(0).def(1))
            .param("tick-particle", D.particle().optional(), "burst spawned on the target every pulse; omit for none")
            .param("tick-particle-count", D.INT.min(0).def(1))
            .param("tick-particle-2", D.particle().optional(),
                    "a SECOND burst on the same pulse, for a cue built from two particle types")
            .param("tick-particle-2-count", D.INT.min(0).def(1))
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Burn the target for amount raw half-hearts every period ticks over duration ticks, "
                    + "attributed to the activator (kill credit, era-combat delivery). replace is a "
                    + "comma-separated set of potion effects the burn converts — each named DoT's DAMAGE is "
                    + "cancelled for the whole window while the effect itself is left on the target, icon and "
                    + "particles intact; only WITHER and POISON tick damage, so any other name converts nothing. "
                    + "feedback is sent to a player target on every pulse, and tick-sound / tick-particle play "
                    + "there too (once per pulse, never deduped against the hit's other cues). Two burns on one "
                    + "victim both run: unlike FREEZE, this is not a refreshed window.")
            .example("{ PERIODIC_DAMAGE: { amount: 6, period: 20, duration: 120, replace: WITHER, "
                    + "tick-particle: FLAME, tick-particle-count: 20 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double amount = ctx.dbl("amount");
        int period = ctx.integer("period");
        int duration = ctx.integer("duration");
        List<Integer> replaced = ctx.ids("replace");
        String feedback = ctx.str("feedback");
        // An absent HANDLE never interns, so -1 is unambiguously "no cue" — no id can collide with it.
        int tickSoundId = ctx.args().has("tick-sound") ? ctx.integer("tick-sound") : -1;
        float tickVolume = tickSoundId < 0 ? 0f : (float) ctx.dbl("tick-volume");
        float tickPitch = tickSoundId < 0 ? 0f : (float) ctx.dbl("tick-pitch");
        int tickParticleId = ctx.args().has("tick-particle") ? ctx.integer("tick-particle") : -1;
        int tickParticleCount = tickParticleId < 0 ? 0 : ctx.integer("tick-particle-count");
        int tickParticle2Id = ctx.args().has("tick-particle-2") ? ctx.integer("tick-particle-2") : -1;
        int tickParticle2Count = tickParticle2Id < 0 ? 0 : ctx.integer("tick-particle-2-count");
        for (LivingEntity target : ctx.targets("who")) {
            // The activator attributes every pulse (ADR-0054), exactly as FREEZE's DoT does.
            sink.periodicDamage(target, amount, period, duration, replaced, feedback, ctx.actor(),
                    tickSoundId, tickVolume, tickPitch, tickParticleId, tickParticleCount,
                    tickParticle2Id, tickParticle2Count);
        }
    }
}
