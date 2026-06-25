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
 * Anti-cheat movement exemption (docs/decisions/0027): when StarEnchants applies VELOCITY/TELEPORT itself,
 * briefly tell the installed anti-cheat to ignore the motion so engine knockback/launch/teleport doesn't
 * trip movement false-flags. The composed {@link Consumer} is installed once as the sink's exemption hook.
 *
 * <p>Reflective + fail-safe for the closed/premium anti-cheats (no compile dependency); every call is
 * wrapped so a missing/changed method just means "not exempted", never a throw in the combat path.
 *
 * <p>Coverage: NoCheatPlus via its stable {@code NCPExemptionManager} (reflective, here); GrimAC via
 * {@link Grim} (compiled against GrimAPI, surgical {@code FlagEvent} cancel). Vulcan/Matrix/Spartan expose
 * no runtime exemption API we can implement without a fake no-op, so they are only detected + logged (each
 * handles server velocity/teleport natively); adding a real one later is a localized addition.
 */
public final class AntiCheat {

    /** Long enough for the engine-applied motion to settle past checks. */
    private static final long EXEMPT_WINDOW_TICKS = 20L;

    private AntiCheat() {
    }

    /** One anti-cheat's exempt/unexempt pair; both calls swallow reflective errors. */
    interface Exempter {
        void exempt(Player player);

        void unexempt(Player player);
    }

    /**
     * Build the movement-exemption hook for the anti-cheats present + enabled here, or a no-op when none is
     * actionable. Exempting holds {@link #EXEMPT_WINDOW_TICKS} then auto-unexempts on the player's region
     * scheduler (Folia-correct).
     */
    public static Consumer<Player> exemption(Plugin plugin, Predicate<String> enabled, System.Logger log) {
        // Exempt now, auto-unexempt after the window (NoCheatPlus).
        List<Exempter> windowed = new ArrayList<>();
        // Fire-and-forget motion recorders; a flag listener cancels coinciding flags (GrimAC).
        List<Consumer<Player>> recorders = new ArrayList<>();

        if (present(plugin, "NoCheatPlus") && enabled.test("nocheatplus")) {
            Exempter ncp = NoCheatPlusExempter.tryCreate();
            if (ncp != null) {
                windowed.add(ncp);
                log.log(System.Logger.Level.INFO, "anti-cheat: NoCheatPlus exemption active for engine-applied movement");
            }
        }
        if (present(plugin, "GrimAC") && enabled.test("grim")) {
            recorders.add(Grim.install(plugin, log));
        }
        // Detected-but-unsupported: log guidance rather than ship unverifiable reflection.
        noteUnsupported(plugin, enabled, log, "Vulcan", "vulcan");
        noteUnsupported(plugin, enabled, log, "Matrix", "matrix");
        noteUnsupported(plugin, enabled, log, "Spartan", "spartan");

        if (windowed.isEmpty() && recorders.isEmpty()) {
            return player -> { };
        }
        List<Exempter> exempters = List.copyOf(windowed);
        List<Consumer<Player>> moves = List.copyOf(recorders);
        return player -> {
            for (Consumer<Player> recorder : moves) {
                recorder.accept(player);
            }
            if (!exempters.isEmpty()) {
                for (Exempter e : exempters) {
                    e.exempt(player);
                }
                Scheduling.onEntityLater(player, EXEMPT_WINDOW_TICKS, () -> {
                    for (Exempter e : exempters) {
                        e.unexempt(player);
                    }
                });
            }
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
     * NoCheatPlus exemption via its public {@code NCPExemptionManager}, reflective so the core never compiles
     * against NCP. Resolves the {@code exemptPermanently}/{@code unexempt} handles and {@code CheckType.MOVING}
     * once; {@link #tryCreate} returns null if NCP's API isn't as expected (then NCP simply isn't exempted).
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
                return null; // unexpected NCP API — decline rather than misbehave
            }
        }

        @Override
        public void exempt(Player player) {
            try {
                exempt.invoke(null, player.getUniqueId(), movingCheckType);
            } catch (ReflectiveOperationException ignored) {
                // fail-safe: not exempting never breaks the hit
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
