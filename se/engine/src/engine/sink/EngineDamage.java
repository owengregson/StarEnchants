package engine.sink;

/**
 * Marks the frames in which StarEnchants itself is applying entity damage (ADR-0054). Engine-issued
 * damage is attributed to an attacker where one is in scope — so downstream plugins see a real
 * {@link org.bukkit.event.entity.EntityDamageByEntityEvent} instead of an ownerless CUSTOM hurt — which
 * means SE's own combat listeners now hear those events too. This frame preserves the re-entrancy
 * mechanism the old bare {@code hurt()} provided structurally ("no damager → cannot re-enter the combat
 * dispatch"): the dispatch and the proc-adjacent accumulators check {@link #active()} and stand down, so
 * a reflect can never proc a reflect and a DoT tick never advances combos or rage.
 *
 * <p>Thread-confined by construction: Bukkit fires damage events synchronously inside the
 * {@code Entity.damage} call, on the thread that owns the target (the deferred plan already routed
 * there), so a ThreadLocal depth is exact on Paper and Folia alike. No teardown to leak — the frame
 * always unwinds in {@code finally}, so the depth is transiently non-zero only inside one damage
 * application (never a G2-c boot-time installer).
 */
public final class EngineDamage {

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private EngineDamage() {
    }

    /** Whether the current thread is inside an engine-issued damage application. */
    public static boolean active() {
        return DEPTH.get() > 0;
    }

    /**
     * Run {@code body} (the actual {@code Entity.damage} call) inside an engine-damage frame. The sink's
     * {@code hurt()} is the production caller; listener tests use it to stage engine-issued frames.
     */
    public static void frame(Runnable body) {
        DEPTH.set(DEPTH.get() + 1);
        try {
            body.run();
        } finally {
            DEPTH.set(DEPTH.get() - 1);
        }
    }
}
