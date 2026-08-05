package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/**
 * {@code PROXIMITY_ANNOUNCE} — tell everyone nearby that something just happened to the target, so their own
 * {@code PROXIMITY_EVENT} abilities can react to it. The trigger's other source is a player dying; this is the
 * authored one, and {@code tag} is what keeps the two (and any later third) apart.
 *
 * <p>Deliberately its own effect rather than a flag on whatever effect did the thing: the announcement is a
 * separate, optional, radius-bearing decision, and hanging it off {@code SET_VAR} would mean every counter
 * write in the pack grew a nearby scan it never uses.
 */
public final class ProximityAnnounceEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("PROXIMITY_ANNOUNCE")
            .param("tag", D.STRING, "what happened, read by an observer as %proximityevent%")
            .param("radius", D.DOUBLE.min(1).max(64).def(7), "how far the news carries")
            .target("who", T.VICTIM)
            .affinity(Affinity.REGION)
            .doc("Fire PROXIMITY_EVENT on every player within radius of each target — never the target "
                    + "themselves — with tag readable as %proximityevent%. The observer's activation carries "
                    + "the target as its victim, so %distance%, %victim.relation% and every %victim.*% read "
                    + "(including %victim.var.<name>%) describes the subject rather than the observer. The "
                    + "tag exists because one trigger carries several unrelated observations: without it an "
                    + "ally-death watcher and an ally-bleeding watcher would each proc on the other's event.")
            .example("{ PROXIMITY_ANNOUNCE: { tag: \"bleed\", radius: 7, who: \"@Victim\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        String tag = ctx.str("tag");
        double radius = ctx.dbl("radius");
        for (LivingEntity target : ctx.targets("who")) {
            sink.announceProximity(target, tag, radius);
        }
    }
}
