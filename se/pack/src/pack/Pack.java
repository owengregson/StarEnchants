package pack;

import java.util.Map;
import java.util.Objects;

/** A pack read fully into memory (ADR-0023): manifest + forward-slash-keyed file bytes ({@code pack.yml} excluded). */
public record Pack(PackManifest manifest, Map<String, byte[]> files) {

    public Pack {
        Objects.requireNonNull(manifest, "manifest");
        files = Map.copyOf(files);
    }
}
