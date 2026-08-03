package engine.sink;

/**
 * A rider requested for the current bow shot's projectile (PROJECTILE_DRESSING), carried as a read-back because
 * the fired projectile exists only on the event, never in an effect's hands.
 *
 * @param entityTypeId     the interned rider type (§9)
 * @param ttlTicks         the rider's own lifetime cap, the backstop for a projectile that never reports landing
 * @param invulnerableTicks how long the rider ignores damage — long enough that its own flight cannot kill it
 * @param noPickup         whether the rider is barred from picking items up in flight
 */
public record ProjectileDressing(int entityTypeId, int ttlTicks, int invulnerableTicks, boolean noPickup) {
}
