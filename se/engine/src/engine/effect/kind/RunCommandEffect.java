package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code RUN_COMMAND} — run a command from the console (docs/architecture.md §7).
 * {@link Affinity#GLOBAL}: routed to the global thread, not the firing region thread.
 */
public final class RunCommandEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("RUN_COMMAND")
            .param("command", D.STRING)
            .affinity(Affinity.GLOBAL)
            .doc("Run a command from the console. Affinity GLOBAL — runs on the global thread.")
            .example("RUN_COMMAND:eco give %player% 100")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        sink.consoleCommand(ctx.str("command"));
    }
}
