package bootstrap;

import compile.Compiler;
import compile.load.ItemsLoader;
import compile.load.LangLoader;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.load.MasterConfigLoader;
import compile.load.MenusLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import pack.Pack;
import pack.PackManifest;
import pack.PackStore;
import platform.caps.Capabilities;
import schema.diag.DiagCode;
import schema.diag.Diagnostic;
import schema.diag.Source;

/**
 * The {@code /se pack apply} pre-flight (ADR-0046): compares the pack's stamped registry fingerprint with the
 * live one, then dry-run compiles the WHOLE pack surface (content/ through the real {@link LibraryLoader} +
 * the live compiler; items/config/lang/menus through their loaders) in a throwaway staging dir — before a
 * single live file is touched. Synchronous; callers run it off-thread. The fingerprint EXPLAINS a failure
 * fast; the dry-run is the truth (a fingerprint MATCH can still carry compile errors, a MISMATCH can still
 * compile clean), so the fingerprint check never aborts by itself.
 *
 * <p>The pack's declared {@code min-server} floor (R-QC11) is checked FIRST and does abort by itself: below its
 * floor a pack's era handles resolve to nothing, so the compile would bury the one real cause under dozens of
 * {@code E_UNKNOWN_HANDLE}s.
 */
public final class PackGate {

    public enum FingerprintStatus { MATCH, MISMATCH, UNSTAMPED }

    /**
     * One pre-flight verdict. {@code liveFingerprint} and {@code liveSurface} are the SAME gate-time registry
     * snapshot — both captured in one {@link #check} read so the apply's backup stamps a consistent pair.
     * {@code abilityCount} is what the pack WOULD load.
     */
    public record Report(FingerprintStatus status, String liveFingerprint, String liveSurface,
                         String packFingerprint, int abilityCount, List<Diagnostic> diagnostics) {

        public Report {
            diagnostics = List.copyOf(diagnostics);
        }

        public List<Diagnostic> blocking() {
            return diagnostics.stream().filter(Diagnostic::blocking).toList();
        }

        public long warningCount() {
            return diagnostics.stream().filter(d -> !d.blocking()).count();
        }

        public boolean clean() {
            return blocking().isEmpty();
        }
    }

    private final Supplier<Compiler> compilers;
    private final Supplier<String> liveFingerprint;
    private final Supplier<String> liveSurface;
    private final Capabilities caps;

    public PackGate(Supplier<Compiler> compilers, Supplier<String> liveFingerprint, Supplier<String> liveSurface,
                    Capabilities caps) {
        this.compilers = Objects.requireNonNull(compilers, "compilers");
        this.liveFingerprint = Objects.requireNonNull(liveFingerprint, "liveFingerprint");
        this.liveSurface = Objects.requireNonNull(liveSurface, "liveSurface");
        this.caps = Objects.requireNonNull(caps, "caps");
    }

    /** Whether {@code caps} meets a pack's declared {@code min-server} floor (R-QC11); no floor = every era. */
    public static boolean meetsFloor(PackManifest manifest, Capabilities caps) {
        if (manifest.minServer().isBlank()) {
            return true;
        }
        int[] floor = Capabilities.parseVersion(manifest.minServer());
        return caps.atLeast(floor[0], floor[1], floor[2]);
    }

    /** The ONE wording the apply gate and the boot warning share for a pack below its declared floor. */
    public static String belowFloor(PackManifest manifest, Capabilities caps) {
        return "config pack '" + manifest.name() + "' is declared modern-only: it needs Minecraft "
                + manifest.minServer() + " or newer, and this server is "
                + caps.major() + "." + caps.minor() + "." + caps.patch()
                + ". Its sounds, particles and materials do not exist on this era. The bundled defaults and"
                + " cosmic-pack are legacy-capable — use one of those instead.";
    }

    /** Recomputed per call — an addon may have registered between boot and this apply. */
    public String liveFingerprint() {
        return liveFingerprint.get();
    }

    public String liveSurface() {
        return liveSurface.get();
    }

    public Report check(Pack pack) {
        String packFp = pack.manifest().fingerprint();
        // Fingerprint + surface from ONE gate-time read: the apply stamps its backup from both, so they must
        // describe the same live snapshot (a second surface walk could race an addon registering in between).
        String liveFp = liveFingerprint.get();
        String liveSfc = liveSurface.get();
        FingerprintStatus status = packFp.isEmpty() ? FingerprintStatus.UNSTAMPED
                : packFp.equals(liveFp) ? FingerprintStatus.MATCH : FingerprintStatus.MISMATCH;

        // R-QC11: abort on the DECLARATION, not on the handle storm it causes — a modern-only pack compiled on
        // 1.8.9 reports dozens of E_UNKNOWN_HANDLE that all say the same thing badly.
        if (!meetsFloor(pack.manifest(), caps)) {
            return new Report(status, liveFp, liveSfc, packFp, 0,
                    List.of(Diagnostic.error(DiagCode.E_PACK_ERA, belowFloor(pack.manifest(), caps),
                            Source.ofFile(pack.manifest().name() + "/" + PackManifest.ENTRY))));
        }

        Path scratch = null;
        try {
            scratch = Files.createTempDirectory("se-pack-gate");
            PackStore.stageInto(pack.files(), scratch); // out-of-surface entries silently skipped

            // Reload-report order: content first, then the §L sources in their wiring order. Every loader
            // tolerates an absent path, so a partial pack gates on exactly what it carries.
            List<Diagnostic> diagnostics = new ArrayList<>();
            Library library = LibraryLoader.load(scratch.resolve("content"), compilers.get(), 0);
            diagnostics.addAll(library.diagnostics());
            diagnostics.addAll(ItemsLoader.load(scratch.resolve("items")).diagnostics());
            diagnostics.addAll(MasterConfigLoader.load(scratch.resolve("config.yml")).diagnostics());
            diagnostics.addAll(LangLoader.load(scratch.resolve("lang.yml")).diagnostics());
            diagnostics.addAll(MenusLoader.load(scratch.resolve("menus")).diagnostics());
            return new Report(status, liveFp, liveSfc, packFp, library.snapshot().abilityCount(), diagnostics);
        } catch (IOException io) {
            // Only staging I/O throws (ADR-0042 loaders never do); carry it as one blocking diagnostic → abort.
            return new Report(status, liveFp, liveSfc, packFp, 0,
                    List.of(Diagnostic.error(DiagCode.E_CONFIG_IO,
                            "pack pre-check staging failed: " + io.getMessage(), Source.UNKNOWN)));
        } finally {
            deleteTree(scratch);
        }
    }

    /** Best-effort reverse-order delete of the throwaway staging tree; a stranded temp dir is harmless. */
    private static void deleteTree(Path root) {
        if (root == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // leave it for the OS temp sweep
                }
            });
        } catch (IOException ignored) {
            // nothing to clean up
        }
    }
}
