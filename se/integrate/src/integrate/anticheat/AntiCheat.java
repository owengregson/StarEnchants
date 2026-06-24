package integrate.anticheat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import platform.sched.Scheduling;

/**
 * Anti-cheat movement exemption (docs/decisions/0027): when StarEnchants moves a player itself
 * (VELOCITY / TELEPORT effects via the sink), this briefly tells the installed anti-cheat to ignore the
 * motion, so engine-applied knockback/launch/teleport doesn't trip movement false-flags. The composed
 * {@link Consumer} is installed once at boot as the sink's movement-exemption hook.
 *
 * <p><b>Reflective, fail-safe.</b> Anti-cheats are mostly closed/premium with no public maven API, so this
 * uses reflection (no compile dependency) and every call is wrapped — a missing/changed method simply means
 * "not exempted" (the status quo), never a thrown exception in the combat path.
 *
 * <p><b>Verification status (honest).</b> <strong>NoCheatPlus</strong> has a stable, public, well-known
 * exemption API ({@code NCPExemptionManager.exemptPermanently(UUID, CheckType.MOVING)} + {@code unexempt}),
 * implemented here. Grim, Vulcan, Matrix and Spartan are closed/premium and/or expose no runtime
 * exemption API that can be implemented blind without risking a no-op that pretends to work; this class
 * <em>detects</em> them and logs guidance (most movement checks already honor server-applied velocity /
 * teleport events, and each of those anti-cheats provides its own velocity/bypass handling) rather than
 * shipping unverifiable reflection. Adding a real exemption for one of them later is a localized
 * {@link Exempter}.
 */
public final class AntiCheat {

    /** How long to hold the exemption — long enough for the engine-applied motion to settle past checks. */
    private static final long EXEMPT_WINDOW_TICKS = 20L;

    private AntiCheat() {
    }

    /** One anti-cheat's exemption pair; both calls are fail-safe (a thrown reflective error is swallowed). */
    interface Exempter {
        void exempt(Player player);

        void unexempt(Player player);
    }

    /**
     * Build the movement-exemption hook for the anti-cheats present + enabled on this server, or a no-op when
     * none is actionable. Exempting holds for {@link #EXEMPT_WINDOW_TICKS} then auto-unexempts (per the
     * player's region scheduler, Folia-correct).
     */
    public static Consumer<Player> exemption(Plugin plugin, Predicate<String> enabled, System.Logger log) {
        List<Exempter> active = new ArrayList<>();
        if (present(plugin, "NoCheatPlus") && enabled.test("nocheatplus")) {
            Exempter ncp = NoCheatPlusExempter.tryCreate();
            if (ncp != null) {
                active.add(ncp);
                log.log(System.Logger.Level.INFO, "anti-cheat: NoCheatPlus exemption active for engine-applied movement");
            }
        }
        // Detected-but-unsupported anti-cheats: log guidance rather than ship unverifiable reflection.
        noteUnsupported(plugin, enabled, log, "Grim", "grim");
        noteUnsupported(plugin, enabled, log, "Vulcan", "vulcan");
        noteUnsupported(plugin, enabled, log, "Matrix", "matrix");
        noteUnsupported(plugin, enabled, log, "Spartan", "spartan");

        if (active.isEmpty()) {
            return player -> { };
        }
        List<Exempter> exempters = List.copyOf(active);
        return player -> {
            for (Exempter e : exempters) {
                e.exempt(player);
            }
            Scheduling.onEntityLater(player, EXEMPT_WINDOW_TICKS, () -> {
                for (Exempter e : exempters) {
                    e.unexempt(player);
                }
            });
        };
    }

    private static void noteUnsupported(Plugin plugin, Predicate<String> enabled, System.Logger log,
                                        String pluginName, String configKey) {
        if (present(plugin, pluginName) && enabled.test(configKey)) {
            log.log(System.Logger.Level.INFO, "anti-cheat: " + pluginName + " detected — StarEnchants applies"
                    + " movement through server velocity/teleport events that " + pluginName + " handles natively;"
                    + " use its own velocity/bypass settings if engine knockback is ever flagged.");
        }
    }

    private static boolean present(Plugin plugin, String name) {
        Plugin found = plugin.getServer().getPluginManager().getPlugin(name);
        return found != null && found.isEnabled();
    }

    /**
     * NoCheatPlus exemption via its public {@code NCPExemptionManager} — reflective so the core never compiles
     * against NCP. Resolves the static {@code exemptPermanently(UUID, CheckType)} / {@code unexempt(UUID,
     * CheckType)} handles and the {@code CheckType.MOVING} constant once; {@link #tryCreate} returns null if
     * NCP's API is not as expected (then NCP simply isn't exempted).
     */
    private static final class NoCheatPlusExempter implements Exempter {

        private final Method exempt;
        private final Method unexempt;
        private final Object movingCheckType;

        private NoCheatPlusExempter(Method exempt, Method unexempt, Object movingCheckType) {
            this.exempt = exempt;
            this.unexempt = unexempt;
            this.movingCheckType = movingCheckType;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        static Exempter tryCreate() {
            try {
                Class<?> manager = Class.forName("fr.neatmonster.nocheatplus.hooks.NCPExemptionManager");
                Class<?> checkType = Class.forName("fr.neatmonster.nocheatplus.checks.CheckType");
                Object moving = Enum.valueOf((Class<Enum>) checkType.asSubclass(Enum.class), "MOVING");
                Method exempt = manager.getMethod("exemptPermanently", UUID.class, checkType);
                Method unexempt = manager.getMethod("unexempt", UUID.class, checkType);
                return new NoCheatPlusExempter(exempt, unexempt, moving);
            } catch (ReflectiveOperationException | RuntimeException unsupported) {
                return null; // NCP's API isn't what we expect on this version — decline rather than misbehave
            }
        }

        @Override
        public void exempt(Player player) {
            try {
                exempt.invoke(null, player.getUniqueId(), movingCheckType);
            } catch (ReflectiveOperationException ignored) {
                // fail-safe: not exempting is the status quo, never break the hit
            }
        }

        @Override
        public void unexempt(Player player) {
            try {
                unexempt.invoke(null, player.getUniqueId(), movingCheckType);
            } catch (ReflectiveOperationException ignored) {
                // fail-safe
            }
        }
    }
}
