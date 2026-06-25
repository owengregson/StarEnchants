# Adding a command

The plugin has a single command, `/se`, with subcommands. Adding one is local: a
`CommandInfo` entry (which drives both tab-completion *and* the generated docs),
a dispatch `case`, an optional argument completer, and a few `command.*` lang
keys. This guide walks the real `/se split` subcommand end to end, then briefly
covers adding a GUI menu.

Everything lives in `se/bootstrap/src/bootstrap/SeCommand.java`
(`public final class SeCommand implements CommandExecutor, TabCompleter`).

## The layers, by example

| Layer | File / symbol | What it owns |
| --- | --- | --- |
| Declaration | `SeCommand.COMMANDS` (`CommandInfo`) | name, args, description; drives completion + docs |
| Dispatch | `SeCommand#onCommand` switch | the subcommand body |
| Completion | `SeCommand#complete` switch | per-subcommand argument completers |
| Lang | `se/bootstrap/resources/lang.yml` | `command.*` usage/feedback |
| Permission | `se/bootstrap/resources/plugin.yml` | the `starenchants.admin` gate |
| Docs | `website/src/data/surface.json` via `regenDocs` | generated from `COMMANDS` |

## Step 1 — declare it in `COMMANDS`

`CommandInfo` is a small record
(`se/bootstrap/src/bootstrap/CommandInfo.java`); `COMMANDS` is the single source
of truth — `SUBCOMMANDS` (completion) and the generated docs both derive from it,
and **its order must match the dispatch switch**. Add an entry:

```java
static final List<CommandInfo> COMMANDS = List.of(
    CommandInfo.of("reload", "[--dry-run]", "Rebuild the content library off-thread and hot-swap it in."),
    CommandInfo.of("give", "<type> <player> [args]", "Give any mintable item to a player."),
    CommandInfo.of("split", "<amount>", "Split souls off the held gem into a new gem."),
    CommandInfo.alias("unenchant", "removeenchant"),
    // …
    CommandInfo.of("menu", "[name]", "Open an in-game GUI."));
```

`CommandInfo.of(name, args, description)` is a real subcommand; `CommandInfo.alias(name, of)`
is a second name for one. `SUBCOMMANDS` updates itself:

```java
static final List<String> SUBCOMMANDS = COMMANDS.stream().map(CommandInfo::name).toList();
```

## Step 2 — dispatch

`SeCommand#onCommand` switches on the lowercased first arg. Add a `case` (aliases
share a label) and write a private handler:

```java
case "split" -> splitSoul(sender, args);
```

The handler follows the standard pattern — player gate, arg-count usage, parse,
then a hop to the player's region thread for any item/inventory work (Folia):

```java
private void splitSoul(CommandSender sender, String[] args) {
    if (!(sender instanceof Player player)) {
        sender.sendMessage(messages.format("command.not-a-player")); return;
    }
    if (args.length < 2) {
        sender.sendMessage(messages.format("command.soul.split-usage")); return;
    }
    int amount;
    try { amount = Integer.parseInt(args[1]); }
    catch (NumberFormatException bad) {
        sender.sendMessage(messages.format("command.error.bad-number", "ARG", args[1])); return;
    }
    Scheduling.onEntity(player, () -> {          // hop to the player's own thread (Folia)
        SoulService.SplitResult result = souls.split(player, amount);
        switch (result.status()) {
            case OK -> player.sendMessage(messages.format("command.soul.split-ok",
                    "MOVED", result.moved(), "REMAINING", result.remaining()));
            // …
        }
    });
}
```

`/se` runs on the command thread, so any item/inventory/entity work must hop via
`platform.sched.Scheduling.onEntity(player, …)` to the target's region thread —
never mutate an item or open an inventory directly in the dispatch body.

## Step 3 — argument completion

The public `onTabComplete` gathers live vocabularies (enchant keys, set keys,
online players, menu names, …) and delegates to the pure, unit-tested static
`SeCommand#complete(...)`. `args[0]` completes from `SUBCOMMANDS` automatically.
Add a `case` to the `args.length == 2` switch for your subcommand's first
argument:

```java
if (args.length == 2) {
    return switch (sub) {
        case "give" -> filter(GIVE_TYPES, args[1]);
        case "enchant", "book", "removeenchant", "unenchant" -> filter(enchantKeys, args[1]);
        case "menu" -> filter(menuNames, args[1]);
        case "split" -> List.of();              // a free-form number — no completion
        default -> List.of();
    };
}
```

`filter` is a case-insensitive prefix match. Deeper argument trees are explicit
`if (sub.equals("give") && args.length == 4)` blocks (e.g. the
`give <type> <player> [type-arg]` tree). If the completer needs a new vocabulary,
thread it through `onTabComplete` and the `complete(...)` signature.

## Step 4 — lang keys

`command.*` keys are dotted, use legacy `&` colours and `{TOKEN}` placeholders,
and live in `se/bootstrap/resources/lang.yml`:

```yaml
command.not-a-player: "&cThat command can only be run by a player."
command.soul.split-usage: "&cUsage: /se split <amount> &7— hold the gem to split."
command.soul.split-ok: "&aSplit off &f{MOVED}&a souls into a new gem &7(this gem keeps {REMAINING})."
command.error.bad-number: "&cThat is not a number: &7{ARG}"
```

Also add a help line to the list-valued `command.usage:` block. Access keys
through `item.lang.Messages`, which reads a live `Supplier<Lang>` so a `/se reload`
swap takes effect on the next call:

- `messages.format(key, "TOKEN", value)` — single line, with `{TOKEN}` pairs.
- `messages.lines(key)` — list-valued keys (e.g. `command.usage`).

Omitted keys fall back to the built-in English defaults in `Lang.defaults()`, so
a missing key never throws.

## Step 5 — permissions

The whole `/se` command requires `starenchants.admin`, declared in
`se/bootstrap/resources/plugin.yml` — subcommand bodies do not re-check it:

```yaml
commands:
  se:
    permission: starenchants.admin
permissions:
  starenchants.admin:
    default: op
```

The only per-subcommand programmatic check in this command is the optional
per-menu permission node in `SeCommand#openMenu`.

## Step 6 — a new service dependency (only if needed)

If your handler needs a service the command does not yet hold, add a constructor
field to `SeCommand` and pass it at the wiring site in
`se/bootstrap/src/bootstrap/StarEnchantsPlugin.java`:

```java
PluginCommand command = getCommand("se");
if (command != null) {
    SeCommand seCommand = new SeCommand(reloader, enchanter, /* …deps… */ messages, contentRoot);
    command.setExecutor(seCommand);
    command.setTabCompleter(seCommand);
}
```

## Step 7 — regenerate the docs

```bash
./gradlew regenDocs
```

`COMMANDS` is read directly by `SurfaceCatalogDriftTest#render` to emit each
command's name/args/description/alias, so adding to `COMMANDS` then running
`regenDocs` updates the docs automatically. It rewrites
`website/src/data/surface.json`; commit it or `./gradlew build` fails the drift
guard. (The `usage:` text in `plugin.yml` is optional flavour — the real help is
`command.usage` in `lang.yml`.)

## Checklist

1. `SeCommand.COMMANDS` — a `CommandInfo.of(...)` (or `.alias(...)`) entry, in
   dispatch order.
2. `SeCommand#onCommand` — a `case` + a private handler (player gate → arg count →
   parse → `Scheduling.onEntity`).
3. `SeCommand#complete` — a `case` for the argument completer (if it takes args).
4. `se/bootstrap/resources/lang.yml` — `command.<name>.*` keys + a `command.usage`
   line.
5. `SeCommand` constructor + `StarEnchantsPlugin` wiring — only if a new service is
   needed.
6. `./gradlew regenDocs` — commit the updated `website/src/data/surface.json`.

## Adding a GUI menu

A GUI menu is a `Menu` (`se/feature/src/feature/menu/Menu.java`) — stateless and
shareable, with per-open state on a `MenuHolder`. The base interface builds a
Folia-safe open for you:

```java
default void open(Player player) {
    MenuHolder holder = new MenuHolder(this);
    render(holder);
    Scheduling.onEntity(player, () -> player.openInventory(holder.getInventory())); // open on the viewer's thread
}
```

Most menus extend `PagedMenu` or `FormMenu` and override only the content hooks.
`EnchanterMenu`:

```java
public final class EnchanterMenu extends PagedMenu<EnchanterOffers.Offer> {
    public EnchanterMenu(/* deps */, Supplier<MenusConfig> menus) {
        super("enchanter", MenuLayout.paged("&3Enchanter"), caps, menus);
    }
    // override items(holder), icon(holder, offer), onSelect(click, offer)
}
```

Register it (in declaration order) in `StarEnchantsPlugin`:

```java
MenuRegistry menus = new MenuRegistry()
        .register(applyMenu)
        .register(new EnchanterMenu(content, unopenedBooks, caps, messages, menusHolder::config))
        // …;
```

`MenuRegistry#names()` feeds `/se menu` tab-completion, and `/se menu <name>` opens
it — so a registered menu needs no command code of its own. Pass
`menusHolder::config` (the live `Supplier<MenusConfig>`) so operator layout
overrides re-apply on `/se reload`.

### Operator layout overrides — `menus/<name>.yml`

A bundled override lives in `se/bootstrap/resources/menus/<name>.yml`:

```yaml
title: "&3Enchanter &8• &7Mystery Books"
filler: "GRAY_STAINED_GLASS_PANE"
```

`MenusLoader#load` keys each file off its stem (`menus/enchanter.yml` →
`enchanter`) into an immutable `MenusConfig`, published atomically in the same
`/se reload` transaction. Each menu merges its programmatic default with the
override per render via `MenuLayout.from(defaultLayout, menus.get().forMenu(name))`
— set override fields win, unset keep the default — so a reload re-lays-out the
next open.

## Verify

```bash
./gradlew build
```

`build` runs the pure completer tests and the drift guard (which forces the doc
regen above). A subcommand that only reads args and calls a service is pure logic —
the unit gate is enough. Run the live Paper **and** Folia matrix when the command
opens an inventory or mutates entities/items in a way you want exercised under
Folia's region threading:

```bash
scripts/run-matrix.sh
```

A green Paper run says nothing about Folia — verify a fresh PASS on both.

## See also

- [Effect engine internals](../internals/effect-engine.md) — `Scheduling` and the
  Folia region/entity thread model.
- [Compiler and config internals](../internals/compiler-and-config.md) — the
  `/se reload` transaction and the menus snapshot.
- [Adding an item type](adding-an-item-type.md) — the `/se give` surface in full.
- [Decision records](../../decisions/) — ADR-0013 (command surface), ADR-0028
  (documentation site).
