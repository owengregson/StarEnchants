package engine.effect;

/** Internal control signal used by gate effects to stop the remaining effects of one ability without faulting it. */
public final class EffectHalt extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public static final EffectHalt INSTANCE = new EffectHalt();

    private EffectHalt() {
        super(null, null, false, false);
    }
}
