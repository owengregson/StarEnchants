package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/**
 * {@code SUPPRESS_INCOMING} — the defender-keyed complement of {@link SuppressEffect}. The window lives on
 * the holder and is consulted per TARGET APPLICATION, so it silences what other people aim at them rather
 * than what they themselves do. Two consults: gate 5 with the activation's victim id, which stops the whole
 * activation, and gate 12 over each effect's remaining resolved targets, which drops a protected CHAIN HOP
 * from that effect's list while the activation stands (owner ruling R-v).
 *
 * <p>{@code SUPPRESS} cannot express "immune to X" from the defender's seat. Its window is keyed on the
 * activator, so the only way to arm one on an attacker is {@code who: "@Attacker"} from a DEFENSE ability —
 * and the attack pass resolves before the defence pass, so that always misses the opening proc of every
 * engagement. The immunity would leak exactly the hit it exists to stop.
 *
 * <p>{@code chance} rolls per INCOMING target application, not once at the arm. An ability's own chance gate
 * rolls when the window is created, which cannot say "half the mastery procs aimed at me fizzle" — the roll
 * has to happen at each thing it might fizzle. A {@code chance} of 100 short-circuits the draw entirely.
 *
 * <p>Maintained-while-worn is the shape every consumer wants, so a re-arm never WEAKENS the window: a PASSIVE
 * ability can re-arm on every lifecycle tick without churning it. Two DIFFERENT abilities arming the same
 * scope+key on one holder — a set and its own matching crystal is the shipped case — resolve to the stronger
 * chance over the later expiry, so which of them fired last in a cycle cannot decide what the holder gets.
 */
public final class SuppressIncomingEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SUPPRESS_INCOMING")
            .param("scope", D.enumOf("ENCHANT", "GROUP", "TYPE", "KIND"))
            .param("key", D.STRING)
            .param("duration", D.TICKS.def(200))
            .param("chance", D.INT.range(1, 100).def(100),
                    "percent rolled per incoming target application; 100 is absolute")
            // Consume-time feedback, as on SUPPRESS: emitted when the window BLOCKS, not when it is armed.
            .param("consumed-message-actor", D.STRING.def(""), "line to the protected holder, when it blocks")
            .param("consumed-message-victim", D.STRING.def(""), "line to the blocked activator, when it blocks")
            .param("consumed-sound", D.sound().optional(), "cue played at the block; omit for silence")
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Make each target IMMUNE to abilities aimed at them: for `duration` ticks, an ability whose "
                    + "enchant/group/type (or, with scope KIND, whose effect head) matches `key` is blocked "
                    + "whenever it lands on the holder. Aimed at them directly it is stopped outright; when "
                    + "they are merely one of several bodies a chain or area effect resolved onto, they alone "
                    + "are skipped and the rest still take it. `chance` rolls per incoming target application. "
                    + "The mirror of SUPPRESS, which silences what its target DOES; this silences what is done "
                    + "TO them, including the opening proc a defensive SUPPRESS can never reach. Re-arming "
                    + "extends the window, so a PASSIVE may hold it open.")
            .example("{ SUPPRESS_INCOMING: { scope: GROUP, key: lifesteal, duration: 100, who: \"@Self\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int scopeKind = ctx.integer("scope"); // erased to ScopeKinds.ENCHANT/GROUP/TYPE/KIND (0/1/2/3)
        int keyId = ctx.integer("key");       // erased to the cooldown-scope interner id (KIND: the effect kindId)
        int duration = ctx.integer("duration");
        int chance = ctx.integer("chance");
        String actorMessage = ctx.str("consumed-message-actor");
        String victimMessage = ctx.str("consumed-message-victim");
        // An absent HANDLE never interns, so -1 is unambiguously "no cue" — no id can collide with it.
        int soundId = ctx.args().has("consumed-sound") ? ctx.integer("consumed-sound") : -1;
        UUID by = ctx.actor() == null ? null : ctx.actor().getUniqueId();
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player p) {
                sink.suppressIncoming(p, scopeKind, keyId, duration, chance, ctx.sourceDefId(),
                        by, actorMessage, victimMessage, soundId);
            }
        }
    }
}
