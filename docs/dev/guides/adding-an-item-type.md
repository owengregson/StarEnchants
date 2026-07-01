# Adding an item type

A **physical item type** is a mintable, stateful item the plugin owns end to end:
its own `items/<name>.yml` config, its own PDC data, a mint that stamps that data
onto an `ItemStack`, a feature service plus listeners for its in-world gesture,
and a `/se give` surface. Adding one is local — a new config record, a key + a
codec, a service + listener, and a few registrations — but it touches four
modules, so this guide walks the real **soul gem** end to end. The soul gem is
the cleanest example: it exercises every layer (own config, own PDC key separate
from the combat blob, a combine-on-click gesture, both a standalone `/se gem` and
`/se give gem`, and its own lang keys).

Modules, in dependency order:
`se/compile` (config) → `se/item` (PDC key + codec + mint primitive) →
`se/feature` (service + listeners) → `se/bootstrap` (composition root + `/se`).

## The layers, by example

| Layer | Soul gem file | What it owns |
| --- | --- | --- |
| Config record | `se/compile/src/compile/load/SoulGemConfig.java` | typed, validated, `defaults()` |
| Config reader | `se/compile/src/compile/load/ItemsLoader.java` | `readSoulGem`, dispatched by `type:` |
| Config snapshot | `se/compile/src/compile/load/ItemsConfig.java` | `Optional<SoulGemConfig>` + `soulGemOrDefault()` |
| Bundled YAML | `se/bootstrap/resources/items/soul-gem.yml` | the shipped default |
| PDC key | `se/item/src/item/codec/ItemKeys.java` | versioned `NamespacedKey` |
| PDC data + codec | `se/item/src/item/codec/SoulData.java`, `SoulCodec.java` | read/write the container |
| Mint + service | `se/feature/src/feature/soul/SoulService.java` | `mintGem(...)`, gameplay |
| Listeners | `se/feature/src/feature/soul/SoulInventoryListener.java`, `SoulInteractListener.java` | the gesture |
| Command | `se/bootstrap/src/bootstrap/SeCommand.java` | `/se gem`, `/se give gem` |
| Lang | `se/compile/resources/lang.yml` (parsed by `Lang.java`) | player feedback |
| Wiring | `se/bootstrap/src/bootstrap/StarEnchantsPlugin.java` | construct + register |

## Step 1 — the bundled YAML

Every physical item is its own top-level file under
`se/bootstrap/resources/items/`, dispatched by a `type:` field.
`items/soul-gem.yml`:

```yaml
type: soul-gem
material: EMERALD
name: "&aSoul Gem"
lore:
  - "&7Souls: {SOUL-COLOR}{AMOUNT}"
  - "&7Right-click to toggle soul mode."
souls-per-kill: 1
```

Item `name`/`lore` are **not** lang keys — they live here, with `{TOKEN}`
placeholders the service substitutes (see Step 5). Only player feedback messages
are lang keys.

## Step 2 — the config record

A `*Config` record is typed, defensively-copied, and carries a `defaults()` that
is the built-in fallback when the YAML is absent. Add
`se/compile/src/compile/load/<Name>Config.java` modelled on `SoulGemConfig`:

```java
public record SoulGemConfig(String material, String name, List<String> lore,
                            int soulsPerKill, /* … */ ) {

    public static SoulGemConfig defaults() {
        return new SoulGemConfig("EMERALD", "&aSoul Gem",
                List.of("&7Souls: {SOUL-COLOR}{AMOUNT}"), 1 /* … */);
    }
    public int soulsFor(String mob) { /* … */ }
    public String colorFor(int souls) { /* … */ }
}
```

## Step 3 — the reader and the snapshot

`ItemsLoader#load(Path itemsRoot)` lists `items/*.yml`, reads each `type:`, and
dispatches in a `switch`. Add a `case` plus a `read<Name>` that falls back to
`defaults()` field by field:

```java
case "soul-gem", "soul_gem", "soulgem" -> {
    if (soulGem.isPresent()) { diags.warning("W_ITEM_DUP", /* … */); }
    else { soulGem = Optional.of(readSoulGem(root, diags)); }
}
// …
private static SoulGemConfig readSoulGem(YamlNode root, Diagnostics diags) {
    SoulGemConfig d = SoulGemConfig.defaults();
    return new SoulGemConfig(
            orDefault(root.string("material"), d.material()),
            orDefault(root.string("name"), d.name()),
            root.has("lore") ? root.stringList("lore") : d.lore(),
            parseInt(root.string("souls-per-kill"), d.soulsPerKill(), root, diags) /* … */);
}
```

YAML keys are kebab-case (`souls-per-kill`), record components camelCase
(`soulsPerKill`). The loader never throws — a bad value becomes a diagnostic and
the default.

Then thread it through the immutable snapshot
`se/compile/src/compile/load/ItemsConfig.java`: add the
`Optional<SoulGemConfig>` component, a `soulGemOrDefault()` accessor that returns
`SoulGemConfig.defaults()` when absent, and include it in both `ItemsConfig.empty()`
and the final `new ItemsConfig(...)` at the end of `load`.

## Step 4 — the PDC key and codec

Item state is one data layer: PDC under versioned keys, never lore parsing.
`ItemKeys` is the single key authority — built once at boot. The soul state is a
**separate key from the `combat` blob** on purpose, so soul spends/gains do not
invalidate the content-hash `ItemView` cache. Add a key field, accessor, and the
`ItemKeys.of(...)` entry:

```java
// se/item/src/item/codec/ItemKeys.java
public static ItemKeys of(Plugin plugin) {
    return new ItemKeys(new NamespacedKey(plugin, "combat"),
                        new NamespacedKey(plugin, "soul"), /* … */);
}
public NamespacedKey soul() { return soul; }
```

> The key strings must never drift, or items written under the old namespace stop
> resolving. Treat `ItemKeys` as append-only.

Add the data record `se/item/src/item/codec/SoulData.java`
(`record SoulData(UUID gemId, int souls)`) and the codec
`se/item/src/item/codec/SoulCodec.java`, constructed with the single key:

```java
public SoulData read(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) return null;
    return decode(stack.getItemMeta().getPersistentDataContainer()
            .get(soulKey, PersistentDataType.STRING));
}
public void write(ItemStack stack, SoulData data) {
    ItemMeta meta = stack.getItemMeta();
    if (meta == null) return;
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    if (data == null) pdc.remove(soulKey);
    else pdc.set(soulKey, PersistentDataType.STRING, data.gemId() + ":" + data.souls());
    stack.setItemMeta(meta);
}
```

## Step 5 — the mint

`ItemFactory` (`se/item/src/item/mint/ItemFactory.java`) is the **generic**
primitive — it resolves a material token cross-version (and ItemsAdder/Oraxen
custom items) and applies a coloured name/lore. It is pure construction with no
entity/world read, so it is safe to call from any thread; the caller chooses the
give thread.

The **item-specific** mint lives on the feature service — it calls
`ItemFactory.build`, substitutes the `{TOKEN}` placeholders, then stamps the PDC.
`SoulService`:

```java
public ItemStack mintGem()           { return mintGemStack(SoulData.fresh(UUID.randomUUID())); }
public ItemStack mintGem(int souls)  { return mintGemStack(new SoulData(UUID.randomUUID(), Math.max(0, souls))); }

private ItemStack mintGemStack(SoulData data) {
    SoulGemConfig cfg = config.get();
    ItemStack gem = ItemFactory.build(cfg.material(), Material.EMERALD, cfg.name(),
                                      renderGemLore(cfg, data.souls()));
    codec.write(gem, data);
    return gem;
}

static List<String> renderGemLore(SoulGemConfig cfg, int souls) {
    String soulColor = cfg.colorFor(souls);
    // line.replace("{AMOUNT}", …).replace("{SOUL-COLOR}", soulColor)
}
```

## Step 6 — the service and listeners

The service holds the config supplier and the codec, mints, and exposes the
gameplay verbs (`isGem`, `toggle`, `combine`, `split`). Listeners are thin and
hop to the right Folia thread for any entity/inventory work.

The combine-on-click gesture — `SoulInventoryListener`:

```java
@EventHandler(ignoreCancelled = true)
public void onClick(InventoryClickEvent event) {
    if (event.getClick() != ClickType.LEFT || !(event.getWhoClicked() instanceof Player player)) return;
    ItemStack cursor = event.getCursor();
    ItemStack current = event.getCurrentItem();
    if (cursor == null || current == null || cursor.getAmount() != 1 || current.getAmount() != 1) return;
    if (!souls.isGem(cursor) || !souls.isGem(current)) return;
    ItemStack merged = souls.combine(player, cursor, current);
    if (merged == null) return;
    event.setCancelled(true);
    event.setCurrentItem(merged);
    player.setItemOnCursor(null);
}
```

The same listener guards `BlockPlaceEvent`, `PrepareItemCraftEvent`, and
`CraftItemEvent` against dupes. The right-click toggle lives in a separate
`SoulInteractListener#onInteract(PlayerInteractEvent)` (main-hand only).

> For an **apply-onto-gear** item (orb, scroll), model the listener on the slot
> orb (`se/feature/src/feature/slot/`): the same `InventoryClickEvent` shape, but
> the handler applies the cursor item onto the clicked gear and consumes one. Its
> lang keys (`slot.apply`, `slot.at-cap`, `slot.not-gear`) show the feedback
> vocabulary an apply gesture needs.

## Step 7 — the `/se give` surface

`SeCommand` exposes items two ways. The unified
`/se give <type> <player> [args]` switch:

```java
case "gem" -> {
    int amount = 0;
    if (args.length >= 4) {
        try { amount = Integer.parseInt(args[3]); }
        catch (NumberFormatException bad) {
            sender.sendMessage(messages.format("command.error.bad-number", "ARG", args[3])); return;
        }
    }
    deliver(sender, target, souls.mintGem(amount), "command.give.gem", "soul gem");
}
```

Add your `case` here and add the type string to the `GIVE_TYPES` list (so it
tab-completes). `deliver(...)` runs on the target's region thread via
`Scheduling.onEntity` and uses `MenuItems.giveOrDrop`. A standalone convenience
verb (like `/se gem`) is optional — see `SeCommand#giveGem`.

## Step 8 — lang keys

Add player-feedback keys in **both** places: the baked catalogue
the bundled catalogue `se/compile/resources/lang.yml` — the single source of truth,
parsed by `Lang.defaults()`. A user's on-disk `lang.yml` overrides any subset; a
missing key renders as `&c<key>?` rather than throwing.

Reference keys through `item.lang.Messages`, which reads a live `Supplier<Lang>`
so `/se reload` re-reads them:

```java
player.sendMessage(messages.format("soul.activate"));
player.sendMessage(messages.format("command.soul.split-ok", "MOVED", moved, "REMAINING", remaining));
```

## Step 9 — wire it at the composition root

`se/bootstrap/src/bootstrap/StarEnchantsPlugin.java` is the only place
everything is constructed and registered. Build the service, register the
listeners behind the feature toggle, and pass the service into `SeCommand`:

```java
SoulService soulService = new SoulService(souls, soulModes,
        new SoulCodec(ItemKeys.of(this).soul()),
        () -> items.config().soulGemOrDefault(),
        () -> master.config().souls().depositOnAnyKill(), messages, particleFx);
// …
if (features.souls()) {
    getServer().getPluginManager().registerEvents(new SoulInteractListener(soulService), this);
    getServer().getPluginManager().registerEvents(new SoulInventoryListener(soulService), this);
}
```

A new item type usually gets a `features.<x>()` switch on the features record in
`se/compile/src/compile/load/MasterConfig.java` so an operator can turn it off.

## Checklist

1. `se/bootstrap/resources/items/<x>.yml` with a `type:` field.
2. `se/compile/.../<Name>Config.java` (record + `defaults()`).
3. `ItemsLoader#load` — a `case` + `read<Name>`.
4. `ItemsConfig` — the `Optional<…>` component, `<x>OrDefault()`, `empty()`, and
   the final constructor.
5. `ItemKeys` — key field, accessor, `ItemKeys.of(...)` entry.
6. `se/item/.../<Name>Data.java` + `<Name>Codec.java`.
7. `se/feature/src/feature/<x>/<Name>Service.java` (with `mint…`) +
   `<Name>Listener.java`.
8. `SeCommand` — a `case` in `give(...)`, the type in `GIVE_TYPES`, the service
   threaded through the constructor.
9. `se/compile/resources/lang.yml` (the message catalogue).
10. Construct + register in `StarEnchantsPlugin` (gate behind a feature toggle).

## Verify

```bash
./gradlew build
```

`build` runs the pure unit tests and the drift guards. An item type that only
mints and stores PDC is pure logic — the unit gate is enough. Run the live Paper
**and** Folia matrix when your **listener** is new (the in-world gesture must
behave under Folia's region/entity threading, which a unit test cannot prove):

```bash
scripts/run-matrix.sh
```

A green Paper run says nothing about Folia — verify a fresh PASS on both.

## See also

- [Item data model internals](../internals/item-data-model.md) — the PDC codec,
  the `ItemView` content-hash cache, the stable-key map, and `WornState`.
- [Compiler and config internals](../internals/compiler-and-config.md) — how an
  `items/*.yml` file becomes a compiled snapshot, and the reload transaction.
- [Adding a command](adding-a-command.md) — the `/se give` surface in full.
- [Adding a config option](adding-a-config-option.md) — adding a feature toggle.
- [Decision records](../../decisions/) — ADR-0005 (item data PDC), ADR-0014
  (content loader and reload).
