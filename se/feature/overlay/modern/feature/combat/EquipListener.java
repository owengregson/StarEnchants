package feature.combat;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import compile.load.ContentHolder;
import feature.trigger.LifecycleDriver;
import feature.trigger.PassiveEffectDriver;
import feature.trigger.RepeatingDriver;
import feature.trigger.SetMessageDriver;
import item.worn.WornStateStore;
import org.bukkit.event.EventHandler;

/**
 * Modern (Paper) {@link WornStateStore} refresher — the era leaf over {@link EquipListenerBase}. Armour
 * changes arrive via Paper's {@code PlayerArmorChangeEvent}; the join / held-item / respawn / quit lifecycle
 * and the refresh pipeline are shared. Same-FQN counterpart to the {@code overlay/legacy} per-tick poll
 * (docs/legacy-1.8.9-codeshare-design.md §6).
 */
public final class EquipListener extends EquipListenerBase {

    public EquipListener(WornStateStore worn, ContentHolder content, RepeatingDriver repeating,
                         LifecycleDriver lifecycle, PassiveEffectDriver passiveEffects, SetMessageDriver setMessages) {
        super(worn, content, repeating, lifecycle, passiveEffects, setMessages);
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        refresh(event.getPlayer());
    }
}
