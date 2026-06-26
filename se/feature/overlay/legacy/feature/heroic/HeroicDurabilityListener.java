package feature.heroic;

import item.codec.CombatCodec;
import java.util.Objects;
import java.util.Random;
import org.bukkit.event.Listener;

/**
 * Legacy (1.8.9) heroic-durability listener — a no-op. 1.8 has no {@code PlayerItemDamageEvent}, so the
 * per-item durability-save chance cannot be applied without an NMS hook; heroic durability is therefore a
 * §6 "degrades" feature on 1.8 (a Phase-2/Gate-4 NMS hook could restore it). Same-FQN counterpart to the
 * {@code overlay/modern} listener, with the same ctor so the composition root type-checks; registered but
 * inert.
 */
public final class HeroicDurabilityListener implements Listener {

    public HeroicDurabilityListener(CombatCodec codec, Random random) {
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(random, "random");
    }
}
