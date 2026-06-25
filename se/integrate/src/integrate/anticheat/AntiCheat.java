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
 * briefly tell the installed anti-cheat to ignore the motion so engine knockback/launch/teleport doesn't trip
 * movement false-flags.
 *
 * <p>Coverage: NoCheatPlus via reflective {@code NCPExemptionManager} (here); GrimAC via {@link Grim}.
 * Vulcan/Matrix/Spartan expose no runtime exemption API, so they are only detected + logged (each handles
 * server velocity/teleport natively).
 */
public final class AntiCheat {

    /** Long enough for the engine-applied motion to settle past checks. */
    private static final long EXEMPT_WINDOW_TICKS = 20L;

    private AntiCheat() {
    }

    /** One anti-cheat's exempt/unexempt pair; both calls swallow reflective errors (fail-safe). */
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
        List<Exempter> windowed = new ArrayList<>(); // exempt now, auto-unexempt after the window (NoCheatPlus)
        List<Consumer<Player>> recorders = new ArrayList<>(); // record motion; a flag listener cancels it (GrimAC)

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

    /** NoCheatPlus exemption via its {@code NCPExemptionManager}; {@link #tryCreate} declines if its API differs. */
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
