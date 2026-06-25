# Cached PaperMC & Folia Developer Documentation

Offline reference for the **StarEnchants** plugin project (Paper 1.17.1 → 1.21.11 + Folia).

All files are the **verbatim markdown source** of the official docs, cached on **2026-06-15**.
Each file begins with its source URL and fetch date. Pages were pulled from the
[`PaperMC/docs`](https://github.com/PaperMC/docs) repo (raw source under `src/content/docs/`),
which is what renders on <https://docs.papermc.io>, so all inline version-specific notes
(`since 1.20.5`, `1.21.4+`, `Experimental`, deprecations, etc.) are preserved intact.

Notes on doc syntax:
- Links like `jd:paper:org.bukkit.NamespacedKey` are the docs' shorthand for javadoc links
  (resolve to <https://jd.papermc.io>). Per-version javadocs are NOT cached here — use the
  local server jars / <https://jd.papermc.io> for the exact per-version API surface.
- Many files retain a `version:` frontmatter field indicating which Minecraft/API version the
  page's code examples target at fetch time.

This is the **prose developer guide** cache only. The full per-version javadoc is intentionally
out of scope (covered separately by local server jars).

---

## Paper (`paper/`)

### Plugin bootstrap, lifecycle & project structure
| File | Topic | Source |
|------|-------|--------|
| `paper/paper-plugins.md` | Paper plugins: `paper-plugin.yml`, `PluginBootstrap`, `PluginLoader`, split `bootstrap`/`server` dependencies, classloader isolation, load ordering (Experimental) | <https://docs.papermc.io/paper/dev/getting-started/paper-plugins/> |
| `paper/plugin-yml.md` | The `plugin.yml` / `paper-plugin.yml` manifest: name/version/main, `api-version`, `depend`/`softdepend`/`loadbefore`/`provides`, libraries, permissions, commands | <https://docs.papermc.io/paper/dev/plugin-yml/> |
| `paper/how-plugins-work.md` | How plugins work in Paper (classloading, load phases, the plugin lifecycle big picture) | <https://docs.papermc.io/paper/dev/how-do-plugins-work/> |
| `paper/lifecycle.md` | Lifecycle API: `LifecycleEventManager`, `BootstrapContext` vs `Plugin`, registering commands/registry edits via lifecycle events, reload constraints | <https://docs.papermc.io/paper/dev/lifecycle/> |
| `paper/userdev-paperweight.md` | `paperweight-userdev` Gradle plugin for Mojang-mapped NMS access (relevant to the 1.20.5 mapping flip) | <https://docs.papermc.io/paper/dev/userdev/> |

### Cross-version / migration reference
| File | Topic | Source |
|------|-------|--------|
| `paper/roadmap-breaking-changes.md` | Roadmap & deprecation policy: planned breaking changes (interface `ItemStack`, `ServerPlayer` reuse), `@Deprecated`/`forRemoval` policy. Closest thing to a cross-version migration guide (see "topics not found" below) | <https://docs.papermc.io/paper/dev/roadmap/> |

### Item data & storage
| File | Topic | Source |
|------|-------|--------|
| `paper/pdc.md` | PersistentDataContainer (PDC): `NamespacedKey`, storing data on items/entities/chunks/world/TileState/etc.; includes the 1.21.1+ read-only PDC view and `editPersistentDataContainer()` (1.21.4+) notes | <https://docs.papermc.io/paper/dev/pdc/> |
| `paper/data-component-api.md` | Data Component API (item data components, 1.20.5+; Experimental). Version-specific item data not representable via `ItemMeta` | <https://docs.papermc.io/paper/dev/data-component-api/> |

### Scheduling
| File | Topic | Source |
|------|-------|--------|
| `paper/scheduler.md` | `BukkitScheduler` (sync/async, delayed, repeating). Includes the Folia note: on Folia, use Folia's schedulers instead | <https://docs.papermc.io/paper/dev/scheduler/> |
| `paper/folia-support.md` | Supporting Paper AND Folia: `folia-supported: true`, and when to use `GlobalRegionScheduler` / `RegionScheduler` / `AsyncScheduler` / `EntityScheduler` | <https://docs.papermc.io/paper/dev/folia-support/> |

### Registries
| File | Topic | Source |
|------|-------|--------|
| `paper/registries.md` | Registries & registry modification on Paper, `RegistryAccess`, `RegistryKey`, registry events (Experimental) | <https://docs.papermc.io/paper/dev/registries/> |

### Command API (Brigadier)
| File | Topic | Source |
|------|-------|--------|
| `paper/command-api-introduction.md` | Intro to Paper's Brigadier command API | <https://docs.papermc.io/paper/dev/command-api/basics/introduction/> |
| `paper/command-api-registration.md` | Registering Brigadier commands (via the lifecycle `COMMANDS` event) | <https://docs.papermc.io/paper/dev/command-api/basics/registration/> |
| `paper/command-api-basic-command.md` | Bukkit-style `BasicCommand` declaration on Brigadier | <https://docs.papermc.io/paper/dev/command-api/misc/basic-command/> |
| `paper/command-api-comparison.md` | Brigadier vs legacy Bukkit `CommandExecutor` comparison | <https://docs.papermc.io/paper/dev/command-api/misc/comparison-bukkit-brigadier/> |

### Event API
| File | Topic | Source |
|------|-------|--------|
| `paper/event-listeners.md` | Listening to events: `@EventHandler`, `Listener`, priorities, `ignoreCancelled` | <https://docs.papermc.io/paper/dev/event-listeners/> |
| `paper/custom-events.md` | Defining & calling custom events | <https://docs.papermc.io/paper/dev/custom-events/> |
| `paper/handler-lists.md` | What an event `HandlerList` is and why it's required | <https://docs.papermc.io/paper/dev/handler-lists/> |

### Adventure / Component text API
| File | Topic | Source |
|------|-------|--------|
| `paper/component-api-intro.md` | Adventure `Component` basics, `TextColor`/`NamedTextColor`, MiniMessage, and legacy `&`/`§`/`#RRGGBB` serialization | <https://docs.papermc.io/paper/dev/component-api/introduction/> |
| `paper/component-api-audiences.md` | Adventure `Audience`s (sending components to players/console/etc.) | <https://docs.papermc.io/paper/dev/component-api/audiences/> |

### Inventories / GUIs
| File | Topic | Source |
|------|-------|--------|
| `paper/custom-inventory-holder.md` | Custom `InventoryHolder` to identify your plugin's GUIs in inventory events | <https://docs.papermc.io/paper/dev/custom-inventory-holder/> |
| `paper/menu-type-api.md` | Menu Type API for building typed menus/views (Experimental) | <https://docs.papermc.io/paper/dev/menu-type-api/> |

---

## Folia (`folia/`)

| File | Topic | Source |
|------|-------|--------|
| `folia/README-threading.md` | **Authoritative** Folia README: region threading model, the four schedulers, what is/isn't safe across threads, plugin-support requirements (from the `ver/1.21.11` branch) | <https://github.com/PaperMC/Folia/blob/ver/1.21.11/README.md> |
| `folia/overview.md` | Abstract overview of how Folia works: independent ticking regions, intra- vs inter-region operations, region invariants | <https://docs.papermc.io/folia/reference/overview/> |
| `folia/region-logic.md` | How Folia's regionizer groups chunks into regions (the exact merge/split logic referenced by the README) | <https://docs.papermc.io/folia/reference/region-logic/> |
| `folia/faq.md` | Folia FAQ (which server types benefit, plugin compatibility expectations, etc.) | <https://docs.papermc.io/folia/faq/> |

> **Folia scheduler API for plugin developers** (`GlobalRegionScheduler`, `RegionScheduler`,
> `EntityScheduler`, `AsyncScheduler` — when to use each, and the `folia-supported: true` flag)
> is documented in `paper/folia-support.md`. The Folia docs site itself has no separate
> developer/scheduler page; that guidance lives in the Paper "Supporting Paper and Folia" page
> plus the Folia README.

---

## Topics requested but NOT available as a dedicated page

- **Single "updating/migrating a plugin across Minecraft versions" dev guide** — Paper's dev docs
  do **not** have one dedicated cross-version migration page (URLs such as
  `/paper/dev/updating/` and `/paper/dev/api/migration/` return 404). The cross-version reference
  value is split across: `paper/roadmap-breaking-changes.md` (planned breaks & deprecation policy)
  and `paper/userdev-paperweight.md` (Mojang mappings / the 1.20.5 mapping flip). Per-release
  breaking-changes details are published in the version announcements at
  <https://papermc.io/news> rather than in the dev docs.
- **Dedicated "ItemStack / ItemMeta / building items" prose page** — there is no standalone page;
  Paper folded that material into `paper/pdc.md` (ItemStack PDC access, the read-only view) and
  `paper/data-component-api.md` (modern item data). For the exact per-version `ItemStack`/`ItemMeta`
  method surface, use the local server jars / <https://jd.papermc.io>.
- **`NamespacedKey` / `Keyed` / `Registry` standalone page** — covered inline within
  `paper/pdc.md` (NamespacedKey usage) and `paper/registries.md` (Keyed/Registry/RegistryAccess).
  There is no separate `NamespacedKey` doc page.
