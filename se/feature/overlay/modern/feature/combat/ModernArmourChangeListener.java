package feature.combat;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Modern (Paper) armour-change feeder for {@link EquipListener} — the era-exclusive {@code overlay/modern}
 * source (ADR-0044; §4). Paper's {@code PlayerArmorChangeEvent} fires the moment armour changes; the 1.8 era
 * instead drives {@link EquipListener#refresh} from the {@code LegacyGearPoll} armour-signature delta.
 */
public final class ModernArmourChangeListener implements Listener {

    private final EquipListener equip;

    public ModernArmourChangeListener(EquipListener equip) {
        this.equip = Objects.requireNonNull(equip, "equip");
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        equip.refresh(event.getPlayer());
    }
}
