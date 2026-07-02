package bootstrap;

import api.spi.AddonAffinity;
import api.spi.AddonEffect;
import api.spi.AddonEffectCtx;
import api.spi.AddonSink;
import api.spi.AddonSpec;
import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.Param;

/**
 * Adapts a public {@link AddonEffect} to the engine's internal {@link EffectKind} (ADR-0038) — the single
 * seam that keeps {@code :api} free of any engine dependency. It translates the {@link AddonSpec} into the
 * engine's {@link EffectSpec} once at construction (mapping {@link AddonAffinity} to {@link Affinity} and the
 * param/target declarations across), then on each activation wraps the engine's {@link Sink} and
 * {@link EffectCtx} in the curated facades the add-on sees.
 *
 * <p>Stateless and hot-path-clean, like every built-in kind. The two thin facade views ({@link SinkView},
 * {@link CtxView}) are the only per-activation allocation; that is acceptable because add-on effects are an
 * opt-in, off-core-spine path, not the built-in combat hot path the JMH gate guards.
 */
final class AddonBridge implements EffectKind {

    private final AddonEffect addon;
    private final EffectSpec spec;

    AddonBridge(AddonEffect addon) {
        this.addon = Objects.requireNonNull(addon, "addon");
        this.spec = toEngineSpec(addon.spec());
    }

    @Override
    public EffectSpec spec() {
        return spec;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        addon.run(new CtxView(ctx), new SinkView(sink));
    }

    @Override
    public void stop(EffectCtx ctx, Sink sink) {
        addon.stop(new CtxView(ctx), new SinkView(sink));
    }

    private static EffectSpec toEngineSpec(AddonSpec addonSpec) {
        EffectSpec.Builder b = EffectSpec.of(addonSpec.head())
                .affinity(Affinity.valueOf(addonSpec.affinity().name())) // enums share constant names 1:1
                .doc(addonSpec.doc())
                .example(addonSpec.example());
        for (Param p : addonSpec.paramSpec().params()) {
            b.param(p.name(), p.type(), p.doc());
        }
        for (AddonSpec.AddonTarget t : addonSpec.targets()) {
            b.target(t.name(), t.selectorType());
        }
        return b.build();
    }

    /** The add-on's read view over one activation's {@link EffectCtx}; forwards each read unchanged. */
    private record CtxView(EffectCtx ctx) implements AddonEffectCtx {
        @Override public double dbl(String name) { return ctx.dbl(name); }
        @Override public int integer(String name) { return ctx.integer(name); }
        @Override public String str(String name) { return ctx.str(name); }
        @Override public boolean bool(String name) { return ctx.bool(name); }
        @Override public Iterable<LivingEntity> targets(String selectorName) { return ctx.targets(selectorName); }
        @Override public int level() { return ctx.level(); }
        @Override public Player actor() { return ctx.actor(); }
        @Override public LivingEntity victim() { return ctx.victim(); }
        @Override public Location location() { return ctx.location(); }
    }

    /** The add-on's curated intent view over one activation's {@link Sink}; each call maps 1:1 to an engine intent. */
    private record SinkView(Sink sink) implements AddonSink {
        @Override public void addOutgoingDamage(double percent) { sink.addOutgoingDamage(percent); }
        @Override public void addDamageReduction(double percent) { sink.addDamageReduction(percent); }
        @Override public void damage(LivingEntity target, double amount) { sink.damage(target, amount); }
        @Override public void potion(LivingEntity t, int id, int amp, int dur) { sink.potion(t, id, amp, dur); }
        @Override public void lightningAndDamage(LivingEntity t, double amount) { sink.lightningAndDamage(t, amount); }
        @Override public void launch(Entity target, double x, double y, double z) { sink.launch(target, x, y, z); }
        @Override public void teleport(Entity target, Location to) { sink.teleport(target, to); }
        @Override public void sound(Location at, int id, float vol, float pitch) { sink.sound(at, id, vol, pitch); }
        @Override public void particle(Location at, int id, int count) { sink.particle(at, id, count); }
        @Override public void spawnEntity(Location at, int type, int count, int ttl, double health) {
            sink.spawnEntity(at, type, count, ttl, health);
        }
        @Override public void blockChange(Location at, int blockDataId) { sink.blockChange(at, blockDataId); }
        @Override public void giveItem(Player t, int materialId, int count) { sink.giveItem(t, materialId, count); }
        @Override public void giveExp(Player target, int amount) { sink.giveExp(target, amount); }
        @Override public void message(Player target, String message) { sink.message(target, message); }
        @Override public void actionBar(Player target, String message) { sink.actionBar(target, message); }
        @Override public void cancelEvent() { sink.cancelEvent(); }
    }
}
