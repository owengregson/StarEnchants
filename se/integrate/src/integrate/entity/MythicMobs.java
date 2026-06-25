package integrate.entity;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import java.util.function.Function;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * MythicMobs integration (docs/decisions/0027): resolves an entity's MythicMob internal name, exposed to
 * conditions as {@code %victim.mobtype%} so an enchant can react to a specific custom mob.
 *
 * <p>Compiled against the real MythicMobs API ({@code compileOnly}) so a renamed accessor is a compile error.
 * Loaded only when MythicMobs is present (gated by the registrar), and fail-safe — a non-MythicMob or any
 * hiccup yields {@code ""}, never an exception in the condition hot path.
 */
public final class MythicMobs {

    private MythicMobs() {
    }

    /** {@code entity → MythicMob internal name}, or {@code ""} when not a MythicMob / MythicMobs unavailable. */
    public static Function<Entity, String> mobType(Plugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("MythicMobs") == null) {
            return entity -> "";
        }
        return entity -> {
            try {
                MythicBukkit mythic = MythicBukkit.inst();
                if (mythic == null || entity == null || !mythic.getAPIHelper().isMythicMob(entity)) {
                    return "";
                }
                ActiveMob mob = mythic.getAPIHelper().getMythicMobInstance(entity);
                return mob == null || mob.getType() == null ? "" : mob.getType().getInternalName();
            } catch (Throwable failed) {
                return ""; // fail-safe: a mob-type lookup must never break a condition evaluation
            }
        };
    }
}
