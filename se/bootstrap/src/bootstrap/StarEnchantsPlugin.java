package bootstrap;

import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import engine.boot.ContentCompiler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import platform.caps.Capabilities;
import platform.content.ContentReloader;
import platform.sched.Scheduling;
import schema.diag.Diagnostic;

/**
 * The StarEnchants plugin — the composition root (ADR-0014). On enable it probes capabilities,
 * installs the {@code Scheduling} backend (Bukkit on Paper, Folia threaded-regions on Folia), copies
 * the shipped default content to the data folder, wires the production {@link Compiler}, loads
 * {@code content/} into the published {@link ContentHolder}, and serves {@code /se reload} through the
 * transactional {@link ContentReloader}. This is the first real plugin main (the tester is test-only).
 *
 * <p>The loaded {@link ContentHolder} is the single published content the engine and item layers read;
 * a fatal content edit on reload keeps the previous snapshot live, so a bad edit never downs the server.
 */
public final class StarEnchantsPlugin extends JavaPlugin {

    /** Shipped default enchants, copied to the data folder on first run (never clobbering edits). */
    private static final List<String> DEFAULT_CONTENT = List.of(
            "content/enchants/lifesteal.yml",
            "content/enchants/scorch.yml",
            "content/enchants/fortify.yml");

    private ContentHolder content;
    private ContentReloader reloader;

    @Override
    public void onEnable() {
        Capabilities caps = Capabilities.probe(getServer());
        Scheduling.init(this, caps);
        getLogger().info("StarEnchants — " + caps + ", scheduling "
                + Scheduling.backend().getClass().getSimpleName());

        saveDefaultContent();
        Path contentRoot = getDataFolder().toPath().resolve("content");

        Compiler compiler = ContentCompiler.production();
        Library initial = LibraryLoader.load(contentRoot, compiler, 0);
        content = new ContentHolder(initial);
        reloader = new ContentReloader(content, compiler, contentRoot, 0);
        logLoad(initial);

        PluginCommand command = getCommand("se");
        if (command != null) {
            command.setExecutor(new SeCommand(reloader));
        }
    }

    /** The single published content the runtime reads. */
    public ContentHolder content() {
        return content;
    }

    private void saveDefaultContent() {
        Path dataFolder = getDataFolder().toPath();
        for (String resource : DEFAULT_CONTENT) {
            if (Files.exists(dataFolder.resolve(resource))) {
                continue; // never clobber an operator's edits
            }
            try {
                saveResource(resource, false);
            } catch (RuntimeException missing) {
                getLogger().warning("could not save default content '" + resource + "': " + missing.getMessage());
            }
        }
    }

    private void logLoad(Library library) {
        long errors = library.diagnostics().stream().filter(Diagnostic::blocking).count();
        getLogger().info("content loaded: " + library.snapshot().abilityCount() + " abilities, "
                + library.diagnostics().size() + " diagnostic(s), " + errors + " error(s)");
        for (Diagnostic diagnostic : library.diagnostics()) {
            getLogger().warning("  " + diagnostic);
        }
    }
}
