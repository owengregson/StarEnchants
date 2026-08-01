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
 * {@code GUARD} — spawn guardian mob(s) that target the attacker (§C combat-flags): a targeted superset of
 * {@code SPAWN_ENTITY} for retaliation. Targets {@link T#ATTACKER}, so with no attacker (a non-combat
 * trigger) it spawns nothing — an untargeted spawn is {@code SPAWN_ENTITY}'s job.
 */
public final class GuardEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("GUARD")
            .param("type", D.entityType())
            .param("count", D.INT.min(1).def(1))
            .param("ttl", D.TICKS.def(200))
            .param("name", D.STRING.def(""))
            .param("health", D.DOUBLE.min(0).def(0), "0 keeps the entity default")
            .param("spawn-y-offset", D.DOUBLE.min(-16).max(16).def(0))
            .param("chunk-cap", D.INT.min(0).def(0), "0 disables the cap")
            .param("retarget-radius", D.DOUBLE.min(0).def(0), "0 disables periodic retargeting")
            .param("retarget-period", D.TICKS.min(1).def(20))
            .param("fire-resistance", D.BOOL.def(false))
            .param("regeneration", D.BOOL.def(false))
            .param("strength", D.BOOL.def(false))
            .param("speed", D.BOOL.def(false))
            .param("resistance", D.BOOL.def(false))
            .param("sound", D.sound().optional())
            .param("sound-volume", D.DOUBLE.min(0).def(1))
            .param("sound-pitch", D.DOUBLE.min(0).def(1))
            .target("who", T.ATTACKER)
            .affinity(Affinity.REGION)
            .doc("Summon count guardian mobs of type at the activation location, each targeting the "
                    + "attacker, auto-removed after ttl ticks (default 200; 0 = permanent); optional custom "
                    + "name. A targeted SPAWN_ENTITY for retaliation — author on DEFENSE.")
            .example("{ GUARD: { type: IRON_GOLEM, count: 1, ttl: 200, name: \"&bGuardian\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        if (ctx.location() == null) {
            return; // nowhere to spawn the guard
        }
        int type = ctx.integer("type");
        int count = ctx.integer("count");
        int ttl = ctx.integer("ttl");
        String name = ctx.str("name");
        if (ctx.actor() != null) {
            name = platform.text.Tokens.sub(name, "ATTACKER", ctx.actor().getName());
        }
        double health = ctx.dbl("health");
        double spawnY = ctx.dbl("spawn-y-offset");
        int chunkCap = ctx.integer("chunk-cap");
        double retargetRadius = ctx.dbl("retarget-radius");
        int retargetPeriod = ctx.integer("retarget-period");
        int potionFlags = (ctx.bool("fire-resistance") ? 1 : 0)
                | (ctx.bool("regeneration") ? 2 : 0)
                | (ctx.bool("strength") ? 4 : 0)
                | (ctx.bool("speed") ? 8 : 0)
                | (ctx.bool("resistance") ? 16 : 0);
        int sound = ctx.args().has("sound") ? ctx.integer("sound") : -1;
        float soundVolume = (float) ctx.dbl("sound-volume");
        float soundPitch = (float) ctx.dbl("sound-pitch");
        // The activation actor owns each summoned guard (ADR-0049: a hit on it fires the owner's GUARDIAN_HURT).
        java.util.UUID owner = ctx.actor() == null ? null : ctx.actor().getUniqueId();
        for (LivingEntity attacker : ctx.targets("who")) {
            sink.guard(attacker, ctx.location(), type, count, ttl, name, owner, health, spawnY, chunkCap,
                    retargetRadius, retargetPeriod, potionFlags, sound, soundVolume, soundPitch);
        }
    }
}
