package feature.fx;

import compile.load.ParticleSpec;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.v1_8_R3.EnumParticle;
import net.minecraft.server.v1_8_R3.PacketPlayOutWorldParticles;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

/**
 * Legacy (1.8.9) particle feedback — same-FQN counterpart to the modern {@code overlay/modern} impl. 1.8
 * has no {@code org.bukkit.Particle}/{@code spawnParticle}, so particles are sent as the NMS
 * {@code PacketPlayOutWorldParticles} resolved by 1.8 {@code EnumParticle} name. Construction differs from
 * the modern impl (no {@code Particle} resolver), but the consumed surface — {@link #NONE} and
 * {@link #spawn} — is identical, so the shared {@code SoulParticleDriver} is blind to the difference.
 * (Routing config tokens through the legacy particle alias table is Gate-3/Phase-3 hardening.)
 */
public final class ParticleFx {

    /** No-op fx for unit/synthetic contexts. */
    public static final ParticleFx NONE = new ParticleFx();

    public ParticleFx() {
    }

    public void spawn(Player player, List<String> tokens, int count) {
        if (player == null || tokens == null || tokens.isEmpty()) {
            return;
        }
        Location at = player.getLocation();
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            EnumParticle particle;
            try {
                particle = EnumParticle.valueOf(token.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException notA1_8Particle) {
                continue; // unknown on 1.8 → skip, never crash
            }
            PacketPlayOutWorldParticles packet = new PacketPlayOutWorldParticles(
                    particle, true, (float) at.getX(), (float) (at.getY() + 1.0), (float) at.getZ(),
                    0.3f, 0.5f, 0.3f, 0.0f, Math.max(1, count));
            ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
        }
    }

    /**
     * Degraded {@link ParticleSpec} spawn on 1.8.9: honours the count, spread, and y-offset, but 1.8 has no
     * coloured-dust API, so the RGB is dropped (a {@code DUST} token also won't resolve here — 1.8 names it
     * {@code REDSTONE}). Same signature as the modern twin so the class sets match for the mega-jar merge.
     */
    public void spawn(Player player, ParticleSpec spec) {
        if (player == null || spec == null || spec.isEmpty()) {
            return;
        }
        EnumParticle particle;
        try {
            particle = EnumParticle.valueOf(spec.type().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException notA1_8Particle) {
            return; // unknown on 1.8 → skip, never crash
        }
        Location at = player.getLocation();
        float s = (float) spec.spread();
        PacketPlayOutWorldParticles packet = new PacketPlayOutWorldParticles(
                particle, true, (float) at.getX(), (float) (at.getY() + spec.yOffset()), (float) at.getZ(),
                s, s, s, 0.0f, Math.max(1, spec.amount()));
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    }
}
