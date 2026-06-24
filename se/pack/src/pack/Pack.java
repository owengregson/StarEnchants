package pack;

import java.util.Map;
import java.util.Objects;

/**
 * A pack read fully into memory (ADR-0023): its {@link PackManifest} plus the config files it carries,
 * keyed by forward-slash relative path (e.g. {@code content/enchants/venom.yml}) with the raw file
 * bytes as the value. The {@code pack.yml} manifest entry is NOT included in {@code files} — it is
 * surfaced separately as {@link #manifest()}.
 *
 * <p>Packs are small (a whole config is a few hundred KB), so reading every entry into a map is fine;
 * it keeps {@link PackStore} apply/extract a pure in-memory transform with no streaming bookkeeping.
 */
public record Pack(PackManifest manifest, Map<String, byte[]> files) {

    public Pack {
        Objects.requireNonNull(manifest, "manifest");
        files = Map.copyOf(files);
    }
}
