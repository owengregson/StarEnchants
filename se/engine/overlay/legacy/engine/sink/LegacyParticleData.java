package engine.sink;

import net.minecraft.server.v1_8_R3.EnumParticle;
import org.bukkit.Material;

/**
 * The trailing varint block the 1.8 particle packet demands — the legacy counterpart of the modern leaf's
 * {@code ParticleDefaults} (ADR-0044), shared by {@link LegacyDispatchSink} and {@code LegacyParticleFx}.
 * {@code PacketPlayOutWorldParticles.b} writes exactly {@code EnumParticle.d()} varints out of the varargs
 * array (javap, craftbukkit-1.8.8), so a short array throws inside the Netty encoder mid-frame. A missing or
 * unusable material degrades to STONE, as it does on modern — one authored particle line, one behaviour.
 */
public final class LegacyParticleData {

    private static final int[] NONE = new int[0];

    private LegacyParticleData() {
    }

    /** The packet's trailing data for {@code particle}; {@code block} may be {@code null} (no material given). */
    @SuppressWarnings("deprecation") // Material.getId(): 1.8 addresses blocks and items by numeric id.
    public static int[] forParticle(EnumParticle particle, Material block) {
        int arity = particle.d();
        if (arity <= 0) {
            return NONE;
        }
        int[] data = new int[arity];
        if (arity == 1) {
            // BLOCK_CRACK / BLOCK_DUST take ONE varint: the combined block state, id + (legacy data << 12)
            // (Block.getCombinedId; getByCombinedId splits it back with & 4095). Nibble 0 = the default state,
            // and a non-block material has no state at all, so it degrades instead of packing garbage.
            data[0] = (block != null && block.isBlock() ? block : Material.STONE).getId();
        } else {
            // ITEM_CRACK takes TWO INDEPENDENT varints, [item id, metadata] — NOT the packed block form above.
            // The Sink's material slot is block data, which the modern leaf cannot feed an item particle
            // either (it falls through to a STONE ItemStack), so both lanes render the neutral STONE item.
            data[0] = Material.STONE.getId();
        }
        return data;
    }
}
