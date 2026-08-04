package engine.sink;

/**
 * One sound + particle burst at a world point, as interned handles plus their volume/pitch/count — the cue a
 * timed field plays at each of its stored points. Both halves are independently inert (a handle below 0, or a
 * zero count), so a field can telegraph with sound only, particles only, or in silence.
 */
public record FieldCue(int soundId, float volume, float pitch, int particleId, int particleCount) {

    public static final FieldCue SILENT = new FieldCue(-1, 0f, 0f, -1, 0);
}
