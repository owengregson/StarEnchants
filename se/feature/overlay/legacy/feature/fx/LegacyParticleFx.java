package feature.fx;

import compile.load.ParticleSpec;
import java.util.List;
import net.minecraft.server.v1_8_R3.EnumParticle;
import net.minecraft.server.v1_8_R3.PacketPlayOutWorldParticles;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import platform.resolve.Aliases;
import platform.resolve.HandleResolver;
import schema.spec.HandleCategory;

/**
 * Legacy (1.8.9) impl of {@link ParticleFx} — the era-exclusive {@code overlay/legacy} particle feedback
 * (ADR-0044; §4). 1.8 has no {@code org.bukkit.Particle}/{@code spawnParticle}, so particles are sent as the NMS
 * {@code PacketPlayOutWorldParticles} resolved by 1.8 {@code EnumParticle} name. Tokens go through the same
 * bidirectional {@link Aliases} table the compiled content DSL resolves against, so a config/likeness token
 * authored in its modern spelling ({@code HAPPY_VILLAGER}) lands on its 1.8 constant ({@code VILLAGER_HAPPY})
 * instead of being dropped.
 */
public final class LegacyParticleFx implements ParticleFx {

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
            if (particle == null || !sendable(particle)) {
                continue;
            }
            PacketPlayOutWorldParticles packet = new PacketPlayOutWorldParticles(
                    particle, true, (float) at.getX(), (float) (at.getY() + 1.0), (float) at.getZ(),
                    0.3f, 0.5f, 0.3f, 0.0f, Math.max(1, count));
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
        if (particle == null || !sendable(particle)) {
            return;
        }
        Location at = player.getLocation();
        float s = (float) spec.spread();
        PacketPlayOutWorldParticles packet = new PacketPlayOutWorldParticles(
                particle, true, (float) at.getX(), (float) (at.getY() + spec.yOffset()), (float) at.getZ(),
                s, s, s, 0.0f, Math.max(1, spec.amount()));
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    }

    /** The 1.8 {@code EnumParticle} a token names in either era's spelling, or {@code null} if 1.8 has none. */
    private static EnumParticle resolve(String token) {
        return HandleResolver.resolve(token, Aliases.forCategory(HandleCategory.PARTICLE), LegacyParticleFx::exists)
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

    /**
     * {@code PacketPlayOutWorldParticles.b} writes {@code EnumParticle.d()} trailing varints out of the varargs
     * array, so BLOCK_CRACK / BLOCK_DUST (1) and ITEM_CRACK (2) throw inside the Netty encoder unless that data is
     * supplied. This surface is a bare particle token with no block or item behind it, so those are skipped.
     */
    private static boolean sendable(EnumParticle particle) {
        return particle.d() == 0;
    }
}
