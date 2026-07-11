package item.worn;

import java.util.List;
import org.bukkit.entity.LivingEntity;

/**
 * The pet contribution to worn state (ADR-0052): the ability STABLE KEYS live for this entity right now.
 * The pets feature owns the whole decision — hotbar scan, level→bracket resolution, the ACTIVE armed-window
 * gate, the {@code features.pets} toggle — and this seam keeps {@code se-item} blind to all of it (the
 * ADR-0035 live-supplier pattern). Called at resolve time on the entity's own thread, NEVER per hit.
 */
public interface PetSource {

    /** No pets (tests, boot before the pets feature wires, the feature disabled). */
    PetSource NONE = entity -> List.of();

    /** The pet ability stable keys to flatten into this entity's {@link WornState}, in a stable order. */
    List<String> liveKeys(LivingEntity entity);
}
