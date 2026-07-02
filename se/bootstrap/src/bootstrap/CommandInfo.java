package bootstrap;

/**
 * One {@code /se} subcommand's metadata. The single source for tab-completion ({@code SeCommand.SUBCOMMANDS}
 * is derived from {@code SeCommand.COMMANDS}) and the generated command docs ({@code website/src/data/surface.json},
 * via {@code SurfaceCatalogDriftTest}) — so the docs list can't drift from the command surface. All {@code /se}
 * commands require {@code starenchants.admin}; the in-game {@code /se help} renders each entry here through the
 * {@code command.help.*} frame in {@code lang.yml} (so the help text can't drift from the surface either).
 */
public record CommandInfo(String name, String args, String description, boolean alias) {

    public static CommandInfo of(String name, String args, String description) {
        return new CommandInfo(name, args, description, false);
    }

    public static CommandInfo alias(String name, String of) {
        return new CommandInfo(name, "", "Alias of /se " + of + ".", true);
    }
}
