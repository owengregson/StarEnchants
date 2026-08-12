package feature.fx;

import compile.load.ParticleSpec;
import engine.sink.LegacyParticleData;
import java.util.List;
import java.util.Map;
import net.minecraft.server.v1_8_R3.EnumParticle;
import net.minecraft.server.v1_8_R3.PacketPlayOutWorldParticles;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import platform.resolve.Aliases;
import platform.resolve.HandleResolver;
import platform.resolve.LegacyFallbacks;
import schema.spec.HandleCategory;

/**
 * Legacy (1.8.9) impl of {@link ParticleFx} — the era-exclusive {@code overlay/legacy} particle feedback
 * (ADR-0044; §4). 1.8 has no {@code org.bukkit.Particle}/{@code spawnParticle}, so particles are sent as the NMS
 * {@code PacketPlayOutWorldParticles} resolved by 1.8 {@code EnumParticle} name. Tokens go through the same
 * renames-plus-1.8-degradations table {@code RenameResolvers} builds for the compiled content DSL, so a
 * modern-spelled token ({@code HAPPY_VILLAGER}) lands on its 1.8 constant ({@code VILLAGER_HAPPY}) and a
 * post-1.8 one ({@code SOUL}) on its degradation, instead of being dropped.
 */
public final class LegacyParticleFx implements ParticleFx {

    /** Renames + this era's lossy degradations — the same table {@code RenameResolvers} builds. */
    private static final Map<String, String> TABLE = Aliases.mergedWith(
            HandleCategory.PARTICLE, LegacyFallbacks.forCategory(HandleCategory.PARTICLE));

    @Override
    public void spawn(Player player, List<String> tokens, int count) {
        if (player == null || tokens == null || tokens.isEmpty()) {
            return;
        }
        Location at = player.getLocation();
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            EnumParticle particle = resolve(token);
            if (particle == null) {
                continue;
            }
            PacketPlayOutWorldParticles packet = new PacketPlayOutWorldParticles(
                    particle, true, (float) at.getX(), (float) (at.getY() + 1.0), (float) at.getZ(),
                    0.3f, 0.5f, 0.3f, 0.0f, Math.max(1, count), data(particle));
            ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
        }
    }

    /**
     * Degraded {@link ParticleSpec} spawn on 1.8.9: honours the count, spread, and y-offset, but 1.8 has no
     * coloured-dust API, so the RGB is dropped (a {@code DUST} spec lands as a plain 1.8 {@code REDSTONE} cloud).
     */
    @Override
    public void spawn(Player player, ParticleSpec spec) {
        if (player == null || spec == null || spec.isEmpty()) {
            return;
        }
        EnumParticle particle = resolve(spec.type());
        if (particle == null) {
            return;
        }
        Location at = player.getLocation();
        float s = (float) spec.spread();
        PacketPlayOutWorldParticles packet = new PacketPlayOutWorldParticles(
                particle, true, (float) at.getX(), (float) (at.getY() + spec.yOffset()), (float) at.getZ(),
                s, s, s, 0.0f, Math.max(1, spec.amount()), data(particle));
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    }

    /** The 1.8 {@code EnumParticle} a token names in either era's spelling, or {@code null} if 1.8 has none. */
    private static EnumParticle resolve(String token) {
        return HandleResolver.resolve(token, TABLE, LegacyParticleFx::exists)
                .map(EnumParticle::valueOf)
                .orElse(null);
    }

    private static boolean exists(String name) {
        try {
            EnumParticle.valueOf(name);
            return true;
        } catch (IllegalArgumentException notA1_8Particle) {
            return false;
        }
    }

    /** No block/item behind a bare token, so a data-carrying one takes the STONE default {@code ModernParticleFx}
     *  gets from {@code ParticleDefaults} — the alternative being a cue that renders on Paper and not here. */
    private static int[] data(EnumParticle particle) {
        return LegacyParticleData.forParticle(particle, null);
    }
}
