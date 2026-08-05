package engine.sink;

/**
 * A rider requested for the current bow shot's projectile (PROJECTILE_DRESSING), carried as a read-back because
 * the fired projectile exists only on the event, never in an effect's hands.
 *
 * @param entityTypeId     the interned rider type (§9); {@code -1} = no rider, dressing only
 * @param ttlTicks         the rider's own lifetime cap, the backstop for a projectile that never reports landing
 * @param invulnerableTicks how long the rider ignores damage — long enough that its own flight cannot kill it
 * @param noPickup         whether the rider is barred from picking items up in flight
 * @param fireTicks        how long the PROJECTILE itself burns — a flaming arrow, which no selector can
 *                         otherwise address: {@code IGNITE} takes its targets from a selector and none names
 *                         a shot in flight. {@code 0} leaves the arrow as it was loosed
 */
public record ProjectileDressing(int entityTypeId, int ttlTicks, int invulnerableTicks, boolean noPickup,
                                 int fireTicks) {

    /** Whether a rider was requested at all — a fire-only dressing carries none. */
    public boolean hasRider() {
        return entityTypeId >= 0;
    }
}
