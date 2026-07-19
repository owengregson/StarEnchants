package feature.combat;

import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * The reflective {@link TimingOverrideBridge} over Mental's (future) {@code HitTimingOverrides} service
 * (StrikeSync {@code project/docs/api-timing-overrides.md}) — capability-detected through Bukkit's
 * ServicesManager, never a compile dependency. A missing CLASS is memoized absent (plugins cannot join
 * the classpath mid-run); a present class with no registration re-probes per call (Mental may enable —
 * and register — after us). Any reflective surprise degrades to false: the caller's fallback write runs.
 */
public final class MentalTimingBridge implements TimingOverrideBridge {

    private static final String SERVICE = "me.vexmc.mental.api.timing.HitTimingOverrides";

    private volatile boolean absent;
    private volatile Class<?> api;
    private volatile Object provider;
    private volatile Method method;

    @Override
    public boolean overrideWindow(UUID victim, UUID attacker, double factor, int durationTicks) {
        if (absent) {
            return false;
        }
        try {
            Object p = provider;
            Method m = method;
            if (p == null || m == null) {
                Class<?> found = api;
                if (found == null) {
                    found = Class.forName(SERVICE);
                    api = found;
                }
                RegisteredServiceProvider<?> reg = Bukkit.getServicesManager().getRegistration(found);
                if (reg == null) {
                    return false; // Mental pre-API or not yet enabled — probe again on the next hit
                }
                p = reg.getProvider();
                m = found.getMethod("overrideWindow", UUID.class, UUID.class, double.class, int.class);
                provider = p;
                method = m;
            }
            m.invoke(p, victim, attacker, factor, durationTicks);
            return true;
        } catch (ClassNotFoundException notInstalled) {
            absent = true;
            return false;
        } catch (Throwable surprise) {
            return false; // a provider unload / signature drift mid-run: fall back, never throw into MONITOR
        }
    }
}
