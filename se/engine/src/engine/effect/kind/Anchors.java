package engine.effect.kind;

import org.bukkit.Location;

/**
 * The shared cue-anchor arithmetic for the location-anchored halves of {@code SOUND} and {@code PARTICLE}
 * (their {@code dy} param). One home so the two kinds cannot drift on what "raise the anchor" means.
 *
 * <p>The entity-anchored halves do NOT come through here: an effect never holds a target's position (that
 * read is the sink's, on the target's own region thread — ADR-0043), so there the offset rides the intent.
 */
final class Anchors {

    private Anchors() {
    }

    /**
     * {@code loc} raised by {@code dy} blocks, or {@code loc} ITSELF when there is no offset.
     *
     * <p>The zero case returns the original deliberately: every line authored before {@code dy} existed passes
     * 0, and those must keep handing the sink the exact point they always did rather than a fresh copy.
     */
    static Location raised(Location loc, double dy) {
        return dy == 0.0 ? loc : loc.clone().add(0.0, dy, 0.0);
    }
}
