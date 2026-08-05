package engine.sink;

import org.bukkit.entity.LivingEntity;

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

    /**
     * Whether the engine-issued damage now being applied declared {@code IGNORE_ARMOR}. Each frame SETS its
     * own answer and restores the caller's on unwind rather than counting depth, so an ordinary hurt nested
     * inside a bypassing one (a listener on the fired event issuing its own) is priced normally, not handed
     * a bypass it never asked for.
     */
    private static final ThreadLocal<Integer> ARMOR_BYPASS = ThreadLocal.withInitial(() -> 0);

    private EngineDamage() {
    }

    /** Whether the current thread is inside an engine-issued damage application. */
    public static boolean active() {
        return DEPTH.get() > 0;
    }

    /**
     * Whether the engine-issued damage now being applied must bypass the victim's armour + enchant-protection
     * reduction ({@code IGNORE_ARMOR}). Read by the combat dispatch, which zeroes the ARMOR/MAGIC modifiers on
     * the event this frame fires — the SAME mechanism that honours the flag on a folded combat hit, because
     * the reduction is the server's and only the event can give it back.
     */
    public static boolean armorBypassed() {
        return ARMOR_BYPASS.get() > 0;
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

    /**
     * The one engine-issued damage application (ADR-0054): attributed when an attacker is in scope and not the
     * target, else bare; always inside the re-entrancy frame. Callers run on the target's owning thread; the
     * attacker handle is only handed to vanilla as the damage source, never dereferenced (Folia-safe).
     */
    public static void hurt(LivingEntity target, double amount, LivingEntity attacker) {
        hurt(target, amount, attacker, false);
    }

    /**
     * As {@link #hurt(LivingEntity, double, LivingEntity)}, but {@code ignoreArmor} marks the frame so the
     * combat dispatch zeroes the fired event's armour + enchant-protection modifiers. This is how a payload
     * walk — an {@code IMPACT} landing, a forced run — honours {@code IGNORE_ARMOR}: it has no folded combat
     * event of its own to hand the flag to, so the flag rides the hurt that BECOMES that event.
     *
     * <p>An ATTRIBUTED hurt only, since that is the one that fires the {@code EntityDamageByEntityEvent} the
     * dispatch reads. An ownerless engine hurt is priced normally — there is no author behind it to bypass
     * armour for, and every payload bite carries its wearer as the attacker.
     */
    public static void hurt(LivingEntity target, double amount, LivingEntity attacker, boolean ignoreArmor) {
        int prior = ARMOR_BYPASS.get();
        ARMOR_BYPASS.set(ignoreArmor ? 1 : 0);
        try {
            frame(() -> apply(target, amount, attacker));
        } finally {
            ARMOR_BYPASS.set(prior);
        }
    }

    private static void apply(LivingEntity target, double amount, LivingEntity attacker) {
        if (attacker != null && !attacker.equals(target)) {
            target.damage(amount, attacker);
        } else {
            target.damage(amount);
        }
    }
}
