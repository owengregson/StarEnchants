package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code PROJECTILE_DRESSING} — seat a rider on the arrow the current {@code BOW_FIRE} activation is firing.
 * An inline read-back like {@code AUTO_LOCK}: the fired projectile exists only on the event, so the bow
 * dispatcher applies the request after the gate walk. Inert on any other trigger, and one rider per shot —
 * rider priority between two dressing enchants is authored, not fought out on the arrow.
 */
public final class ProjectileDressingEffect implements EffectKind {

    public static final EffectSpec SPEC = EffectSpec.of("PROJECTILE_DRESSING")
            .param("type", D.entityType().optional(), "rider to seat on the shot; omit to dress the arrow only")
            .param("ttl", D.TICKS.def(200), "hard cap on the rider's life; the backstop when nothing reports a landing")
            .param("invulnerable", D.TICKS.def(200), "how long the rider ignores damage (0 = never)")
            .param("no-pickup", D.BOOL.def(true))
            .param("fire-ticks", D.TICKS.def(0),
                    "set the ARROW alight for this long (0 = as loosed); no rider needed")
            // AUTO_LOCK's affinity: run() only records a read-back. The rider's own spawn is a REGION op inside
            // the sink, so declaring REGION here would upgrade the whole ability and buy a hop for nothing.
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Ride an entity of type on the projectile this BOW_FIRE activation is loosing — the rider "
                    + "is removed the moment the arrow lands, dies or unloads, and unconditionally after ttl "
                    + "ticks. invulnerable spares it from damage for that many ticks so its own flight cannot "
                    + "kill it; no-pickup stops it hoovering up items in mid-air. One rider per shot: a "
                    + "second PROJECTILE_DRESSING on the same shot replaces the first. fire-ticks lights "
                    + "the ARROW itself, which nothing else can reach — IGNITE takes its targets from a "
                    + "selector and no selector names a shot in flight — and needs no rider, so a flaming "
                    + "arrow is this effect with type omitted. Inert outside a bow shot.")
            .example("{ PROJECTILE_DRESSING: { type: COW, ttl: 200, invulnerable: 200 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int type = ctx.args().has("type") ? ctx.integer("type") : -1;
        sink.dressProjectile(type, ctx.integer("ttl"), ctx.integer("invulnerable"),
                ctx.bool("no-pickup"), ctx.integer("fire-ticks"));
    }
}
