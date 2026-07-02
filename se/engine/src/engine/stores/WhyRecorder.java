package engine.stores;

import java.util.UUID;

/**
 * The pipeline&rarr;store seam: one packed record per {@code ActivationPipeline.evaluate} call (ADR-0045).
 * Primitive-only so {@code engine.stores} never imports {@code engine.pipeline}/{@code compile.model} (no
 * package cycle). Hot path — implementations must be allocation-free and lock-free; {@link #NONE} is the
 * unit-test/bench default (records nothing).
 */
@FunctionalInterface
public interface WhyRecorder {

    void record(UUID actor, long nowTicks, int triggerId, int defId, int verdict, int pA, int pB);

    WhyRecorder NONE = (actor, nowTicks, triggerId, defId, verdict, pA, pB) -> { };
}
