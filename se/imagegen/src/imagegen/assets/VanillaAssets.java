package imagegen.assets;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.yaml.snakeyaml.Yaml;

/**
 * {@link AssetSource} backed by real vanilla Minecraft assets — the textures/models the GUI and sprite renderers
 * composite. All asset bytes flow through one {@link AssetReader} (a directory tree or a zip), so model/texture
 * resolution doesn't care where the assets physically live.
 *
 * <p><b>Asset-location precedence</b> (first that works wins; if none do, {@link #available()} is {@code false} and
 * every lookup returns a placeholder — the generator never fails for want of assets):
 * <ol>
 *   <li>{@code MC_ASSETS_DIR} env — either a directory containing {@code assets/minecraft/...} (an unpacked client
 *       or resource pack) or a path to a client {@code .jar}. Both are supported.</li>
 *   <li>The pinned/overridable client version ({@code -Dse.imagegen.mcVersion} or {@code MC_VERSION} env, default
 *       {@value #DEFAULT_VERSION}): its CLIENT jar is downloaded from Mojang and cached at
 *       {@code build/imagegen/assets/<version>/client.jar} (gitignored, never committed). A present cache is reused.</li>
 * </ol>
 * Network is only touched lazily inside {@link #create()}; any download/IO failure is caught and degrades to an
 * unavailable instance.
 */
public final class VanillaAssets implements AssetSource {

    /** Pinned default client version — overridable via {@code -Dse.imagegen.mcVersion} or {@code MC_VERSION}. */
    public static final String DEFAULT_VERSION = "1.21.1";

    private static final String VERSION_MANIFEST =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private final AssetReader reader;
    private final ModelResolver models;

    private VanillaAssets(AssetReader reader) {
        this.reader = reader;
        this.models = reader == null ? null : new ModelResolver(reader);
    }

    /**
     * Locate assets per the precedence above and return a usable instance. Never throws: on no-assets/no-network it
     * returns an instance whose {@link #available()} is {@code false}.
     */
    public static VanillaAssets create() {
        AssetReader reader = locate();
        return new VanillaAssets(reader);
    }

    private static AssetReader locate() {
        // (a) explicit local dir or jar.
        String dir = System.getenv("MC_ASSETS_DIR");
        if (dir != null && !dir.isBlank()) {
            AssetReader r = openLocal(Path.of(dir.trim()));
            if (r != null) {
                return r;
            }
            warn("MC_ASSETS_DIR='" + dir + "' did not contain usable assets — falling back");
        }
        // (b) cached/downloaded client jar for the pinned version.
        try {
            Path jar = ensureClientJar(version());
            if (jar != null) {
                return new ZipReader(new ZipFile(jar.toFile()));
            }
        } catch (Exception e) {
            warn("could not obtain client jar (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
        }
        return null; // (c) nothing — available() == false.
    }

    /** Open a local path as either a directory of {@code assets/minecraft/...} or a client jar. */
    private static AssetReader openLocal(Path path) {
        try {
            if (Files.isDirectory(path)) {
                Path root = path.resolve("assets").resolve("minecraft");
                if (Files.isDirectory(root)) {
                    return new DirReader(root);
                }
                // Maybe they pointed straight at .../assets/minecraft already.
                if (Files.isDirectory(path.resolve("models")) || Files.isDirectory(path.resolve("textures"))) {
                    return new DirReader(path);
                }
                return null;
            }
            if (Files.isRegularFile(path) && path.toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                return new ZipReader(new ZipFile(path.toFile()));
            }
        } catch (Exception e) {
            warn("failed to open local assets at " + path + " (" + e.getMessage() + ")");
        }
        return null;
    }

    private static String version() {
        String prop = System.getProperty("se.imagegen.mcVersion");
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv("MC_VERSION");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return DEFAULT_VERSION;
    }

    // ---- Mojang client-jar download + cache (build/, gitignored — never committed) ----

    @SuppressWarnings("unchecked")
    private static Path ensureClientJar(String version) throws IOException, InterruptedException {
        Path cache = Path.of("build", "imagegen", "assets", version, "client.jar");
        if (Files.isRegularFile(cache) && Files.size(cache) > 0) {
            return cache; // reuse — don't re-download.
        }
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

        Map<String, Object> manifest = getJson(http, VERSION_MANIFEST);
        String versionUrl = findVersionUrl(manifest, version);
        if (versionUrl == null) {
            warn("version '" + version + "' not found in Mojang manifest");
            return null;
        }
        Map<String, Object> versionJson = getJson(http, versionUrl);
        String clientUrl = clientUrl(versionJson);
        if (clientUrl == null) {
            warn("no client download for version '" + version + "'");
            return null;
        }

        Files.createDirectories(cache.getParent());
        Path tmp = cache.resolveSibling("client.jar.part");
        HttpResponse<InputStream> resp = http.send(
                HttpRequest.newBuilder(URI.create(clientUrl)).timeout(Duration.ofMinutes(5)).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) {
            warn("client jar download HTTP " + resp.statusCode());
            return null;
        }
        try (InputStream in = resp.body()) {
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(tmp, cache, java.nio.file.StandardCopyOption.REPLACE_EXISTING); // atomic publish
        return cache;
    }

    @SuppressWarnings("unchecked")
    private static String findVersionUrl(Map<String, Object> manifest, String version) {
        Object versions = manifest.get("versions");
        if (!(versions instanceof List)) {
            return null;
        }
        for (Object o : (List<Object>) versions) {
            if (o instanceof Map<?, ?> m && version.equals(m.get("id"))) {
                Object url = m.get("url");
                return url instanceof String ? (String) url : null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static String clientUrl(Map<String, Object> versionJson) {
        Object downloads = versionJson.get("downloads");
        if (downloads instanceof Map<?, ?> d && d.get("client") instanceof Map<?, ?> c) {
            Object url = c.get("url");
            return url instanceof String ? (String) url : null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getJson(HttpClient http, String url)
            throws IOException, InterruptedException {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new IOException("HTTP " + resp.statusCode() + " for " + url);
        }
        try (Reader r = new StringReader(resp.body())) {
            Object loaded = new Yaml().load(r);
            return loaded instanceof Map ? (Map<String, Object>) loaded : Map.of();
        }
    }

    // ---- AssetSource ----

    @Override
    public boolean available() {
        return reader != null;
    }

    @Override
    public ResolvedModel resolve(String materialName) {
        return models == null ? ResolvedModel.unknown() : models.resolve(materialName);
    }

    @Override
    public BufferedImage gui(String path) {
        return models == null ? null : models.loadTexture("textures/gui/" + path + ".png");
    }

    @Override
    public BufferedImage blockTexture(String name) {
        return models == null ? null : models.loadTexture("textures/block/" + name + ".png");
    }

    @Override
    public BufferedImage texture(String path) {
        return models == null ? null : models.loadTexture("textures/" + path + ".png");
    }

    private static void warn(String msg) {
        System.err.println("[imagegen] " + msg);
    }

    // ---- one read abstraction; dir + zip impls ----

    /**
     * Reads asset bytes by a path <em>relative to {@code assets/minecraft/}</em> (e.g.
     * {@code "models/item/diamond_sword.json"}, {@code "textures/block/stone.png"}). Returns {@code null} on miss —
     * the rest of the code is agnostic to whether the source is a directory or a zip.
     */
    interface AssetReader {
        byte[] read(String pathUnderAssetsMinecraft);
    }

    /** Reads from an unpacked {@code assets/minecraft/} directory tree. */
    private static final class DirReader implements AssetReader {
        private final Path root; // .../assets/minecraft

        DirReader(Path root) {
            this.root = root;
        }

        @Override
        public byte[] read(String path) {
            try {
                Path p = root.resolve(path).normalize();
                if (!p.startsWith(root) || !Files.isRegularFile(p)) { // contain to the tree
                    return null;
                }
                return Files.readAllBytes(p);
            } catch (Exception e) {
                return null;
            }
        }
    }

    /** Reads from a zip (client jar or resource pack): entries live under {@code assets/minecraft/}. */
    private static final class ZipReader implements AssetReader {
        private final ZipFile zip;

        ZipReader(ZipFile zip) {
            this.zip = zip;
        }

        @Override
        public byte[] read(String path) {
            try {
                ZipEntry entry = zip.getEntry("assets/minecraft/" + path);
                if (entry == null) {
                    return null;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    return in.readAllBytes();
                }
            } catch (Exception e) {
                return null;
            }
        }
    }

    // IMAGEGEN-CONTRACT-GAP: none — AssetSource/ResolvedModel/FlatLayer/BlockModel covered every case the resolver
    // needs (flat layers, full cube, unknown, raw gui/block textures), so no contract was changed or extended.
}
