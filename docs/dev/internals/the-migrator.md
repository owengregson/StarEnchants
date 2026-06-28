# The migrator

The migrator imports existing enchant/armour configurations into StarEnchants'
unified schema. It reads three legacy formats — **EliteEnchantments**,
**EliteArmor**, and **AdvancedEnchantments** — and emits commented,
reviewable content-format-v2 YAML. Its guiding principle: it **never fails on an
un-mappable construct**. It migrates the structure, translates everything it can
verify against the source vocabulary, and flags every gap as an inline
`# TODO port manually` comment plus a diagnostic warning — never a silently-wrong
value, never a crash. This document maps `se/migrate`.

It implements [`docs/architecture.md`](../../architecture.md) §10 and
[ADR-0006](../../decisions/0006-config-and-migration.md) (config & migration),
[ADR-0016](../../decisions/0016-content-format-v2.md) (the output format), and
[ADR-0020](../../decisions/0020-ae-migrator-dsl-coverage.md)
(AE coverage).

> The migrator reuses the same alias maps and `ParamSpec` as the live compiler —
> see [cross-version-api.md](cross-version-api.md) and
> [config-packs.md](config-packs.md). For how a finished port ships as a
> swappable pack, see [config-packs.md](config-packs.md).

## Where it lives

| Concern | File |
| --- | --- |
| Entry point + per-source dispatch | `se/migrate/src/migrate/Migrator.java` |
| EliteEnchantments reader | `se/migrate/src/migrate/EliteEnchantmentsReader.java` |
| EliteArmor reader | `se/migrate/src/migrate/EliteArmorReader.java` |
| AdvancedEnchantments reader | `se/migrate/src/migrate/AdvancedEnchantmentsReader.java` |
| Null-tolerant YAML accessors | `se/migrate/src/migrate/LegacyYaml.java` |
| The translation tables | `se/migrate/src/migrate/Mappings.java` |
| Reviewable YAML emitter | `se/migrate/src/migrate/SchemaWriter.java` |
| v2 effect-line rendering | `se/migrate/src/migrate/V2Effects.java` |
| Intermediate model | `se/migrate/src/migrate/model/Migrated*.java` |

## The flow

```text
legacy YAML  ──reader──>  Migrated* model  ──SchemaWriter──>  content-v2 YAML + # TODOs
                                  │
                              Mappings (verified tables only)
```

A reader parses one legacy format into a format-neutral `MigratedEnchant` /
`MigratedSet` model, translating triggers, item-application groups, effects, and
conditions through `Mappings`. `SchemaWriter` then renders the model to YAML —
loadable content for everything that mapped, the original legacy token kept as a
trailing comment, and anything un-mappable emitted as a `# TODO` line.

## Entry point

`Migrator` (`se/migrate/src/migrate/Migrator.java`) is the static entry point.
Each source has a plain and a `specs`-aware overload (the `Function<String,
ParamSpec>` drives the verbose vs terse v2 effect form):

```java
public static Result eliteEnchantments(String enchantmentsYaml)
public static Result advancedEnchantments(String enchantmentsYaml)
public static Result eliteArmorSet(String id, String setYaml)
// …each also takes a Function<String, ParamSpec> specs overload
```

The `Result` record carries `Map<String, String> files` (relative output path →
YAML) and a `Diagnostics` log. `Result#writeTo(Path)` never clobbers an existing
file and never writes outside the target. Enchants are keyed
`enchants/<id>.yml`, sets `sets/<id>.yml`; an id failing the
`[A-Za-z0-9_-]+` safety pattern is skipped with a `migrate.id` warning.

Warnings — not failures — are emitted for a missing trigger (`migrate.trigger`),
unrecognised applies (`migrate.applies`), and each unmapped effect
(`migrate.effect`).

## The readers

The three formats differ in input shape, so each reader is distinct:

| Aspect | EliteEnchantments | EliteArmor | AdvancedEnchantments |
| --- | --- | --- | --- |
| Wrapper | `Enchants:` map | document root (one set/file) | document root (no wrapper) |
| Display field | `name` | `capitalize(id)` | `display` |
| Applies | `applies` (single string) | hardcoded 4 armour slots | `applies` (list, `ALL_*` groups) |
| Conditions | single `condition:` | n/a | `conditions:` list |
| Compound effects | yes (`WRATH`/`FROST`/`ROT_DECAY`) | no | no |
| Trigger mapper | `Mappings#trigger` | hardcoded `DEFENSE` | `Mappings#aeTrigger` |
| Effect mapper | `Mappings#effects` | `Mappings#setEffect` | `Mappings#aeEffect` |

`LegacyYaml` (`se/migrate/src/migrate/LegacyYaml.java`) wraps a SnakeYAML
`Yaml.load` parse with null-tolerant typed accessors — deliberately *not*
Bukkit's `YamlConfiguration`, so the importer runs on whatever SnakeYAML the
server bundles across the whole range. `LegacyYaml#stringList` coerces a scalar
into a one-element list so a single value and a list of one read alike.

One EE token can expand into several SE effects, so the EE reader flattens:

```java
// se/migrate/src/migrate/EliteEnchantmentsReader.java
for (String token : LegacyYaml.stringList(lvl, "effects")) {
    // One EE token can expand to several SE effects (a WRATH/FROST/ROT_DECAY compound).
    effects.addAll(Mappings.effects(token, defenseDir));
}
```

## The mapping tables

`Mappings` (`se/migrate/src/migrate/Mappings.java`) holds the legacy → unified
vocabulary. **Only heads verified against the legacy source are translated;
every other head becomes a `# TODO`, never a guess.** A few representative
entries as written:

| Legacy effect | SE token |
| --- | --- |
| EE `REPAIR` | `DURABILITY:-1:item` |
| EE `ARMOR_CANCEL` | `IGNORE_ARMOR` |
| EE `DAMAGE_CANCEL` | `DAMAGE_MOD:defense:add:100` |
| AE `ADD_HEALTH` / `HEAL` | `MODIFY_HEALTH:…:give` |
| AE `STEAL_MONEY` | `MODIFY_MONEY:…:transfer` |
| AE `CONSOLE_COMMAND` | `RUN_COMMAND:…` |

Triggers fold legacy variants onto the SE set — EE `BOW_DAMAGE → BOW`,
`REPEATING-<n>` is special-cased to the `REPEATING` trigger plus a period
(`Mappings#repeatTicks`); AE `ATTACK_MOB → ATTACK`, `MINING → MINE`,
`RIGHT_CLICK → INTERACT_RIGHT`, with `;`-separated compound types resolving to
the first recognised segment.

### Direction-aware selectors

A legacy effect's target depends on the trigger *direction* — the same
`@Attacker`/`@Victim` token means different entities on an attack vs a defence
trigger. `Mappings#foe` flips on direction, and the per-token selectors fold the
wielder in correctly:

```java
// se/migrate/src/migrate/Mappings.java
private static String foe(boolean defenseDir) { return defenseDir ? "@Attacker" : "@Victim"; }

// EE target():
case "ATTACKER" -> defenseDir ? "@Attacker" : "@Self"; // on attack, the attacker IS the wielder
case "TARGET", "VICTIM" -> defenseDir ? "@Attacker" : "@Victim";
```

The AE selector (`Mappings#aeSelector`) does the same for `%attacker%` /
`%victim%` / `%target%`; an area or mining selector with no SE equivalent returns
`null`, which turns the effect into a TODO rather than a wrong target.

### Verified conversions

Several conversions are applied uniformly and noted in the output comment:

- **Seconds → ticks (×20).** EE `FLAME` ignite duration, potion durations,
  `repeatTicks`, the `DISABLE_ENCHANTMENT*` durations, AE `BURN`.
- **Random range → max.** A legacy `MIN-MAX` damage range collapses to its upper
  bound, with a note recording the original range.
- **`DISABLE_ENCHANTMENT*` → `SUPPRESS`** (foe-targeted, seconds → ticks):

```java
// se/migrate/src/migrate/Mappings.java
case "DISABLE_ENCHANTMENT" -> ... "SUPPRESS:ENCHANT:enchants/" + parts[1].trim()
        + ":" + (intArg(parts[2]) * 20) + ":" + foe(defenseDirection), ...
case "DISABLE_ENCHANTMENT_GROUP" -> ... "SUPPRESS:GROUP:" + parts[1].trim().toLowerCase(Locale.ROOT) ...
case "DISABLE_ENCHANTMENT_TYPE" -> ... "SUPPRESS:TYPE:" + parts[1].trim().toUpperCase(Locale.ROOT) ...
```

- **AE potion amplifier → SE 1-based level** (`amplifier + 1`), and an AE
  single `=` rewritten to `==` in condition expressions.

### Conditions

EE has one condition per level (`Mappings#eeCondition`), e.g.
`isPlayerBlocking → %blocking% == true`, numeric health checks mapping to
`actor.health` / `victim.health`. AE has a list (`Mappings#aeCondition`) that
distinguishes plain boolean *gates* (`&&`-joined) from a *clause* form
(`%force%` / `%chance%`, which can appear only once at the top level). Type
coherence is enforced so a migrated condition can never produce a blocking
type error at compile time; an unmappable variable returns `null` and becomes a
TODO.

## The `# TODO` discipline

Un-mappability flows through two layers. The model marks it: the default arm of
every `switch` produces `MigratedEffect.todo(legacy, note)` (`se == null`,
`mapped()` false) or `MigratedCondition.todo(reason)`. The writer is the only
place that emits the literal comment:

```java
// se/migrate/src/migrate/SchemaWriter.java#appendEffects
b.append(itemIndent).append("# TODO port manually: ").append(effect.legacy())
        .append(" — ").append(effect.note()).append('\n');
```

Conditions, missing triggers, missing applies-to, an enchant with no levels, and
unrecognised armour pieces each get their own `# TODO` line. The enchant still
loads — it just loads *without that one op*, which the operator then ports by
hand.

## Reviewable output

`SchemaWriter` (`se/migrate/src/migrate/SchemaWriter.java`) emits content-v2 YAML
designed to be read as a diff, not trusted blindly. Every mapped effect keeps the
original legacy token as a trailing comment, and the file carries a header naming
the source plugin and id:

```java
// se/migrate/src/migrate/SchemaWriter.java#appendEffects
if (effect.mapped()) {
    b.append(itemIndent).append("- ").append(V2Effects.item(effect.se(), specs));
    b.append("  # from ").append(effect.legacy());
    if (!effect.note().isBlank()) { b.append(" — ").append(effect.note()); }
    b.append('\n');
}
```

Source file/line numbers are not preserved in the YAML — that detail lives in the
separate `Diagnostics` log; the YAML preserves the original legacy *token*.
`V2Effects` (`se/migrate/src/migrate/V2Effects.java`) renders each mapped token
in the verbose map form when a `ParamSpec` resolves the head (mapping positional
args onto named params), and falls back to a quoted terse string otherwise.

## The `/se migrate` flow

The command lives in `se/bootstrap/src/bootstrap/SeCommand.java` (the single
`/se` admin command). `/se migrate <ee|ea|ae> <path>` runs the import off-thread
and dispatches by format:

```java
// se/bootstrap/src/bootstrap/SeCommand.java#migrate
Migrator.Result result = switch (format) {
    case "ee" -> Migrator.eliteEnchantments(Files.readString(source, ...), migrateSpecs);
    case "ea" -> migrateArmorDir(source, migrateSpecs);   // merges every *.yml in the dir
    case "ae" -> Migrator.advancedEnchantments(Files.readString(source, ...), migrateSpecs);
    default -> null;
};
int written = result.writeTo(migrationTarget);
```

The output is written to `<dataFolder>/migrated/` — **not** the live config — so
an operator reviews the result and resolves every `# TODO` before promoting it.
A finished port can then be packaged as a swappable config pack (the bundled
`cosmic-pack` pack is exactly this: the full EE library run through the
extended migrator — see [config-packs.md](config-packs.md)).

## Gotchas

- **Never guess a head.** If a legacy effect isn't verified in `Mappings`, it
  must become a `# TODO`, not an approximate translation.
- **Migration output is for review, not for live load.** It lands in `migrated/`
  with TODOs intact; promoting it unreviewed defeats the design.
- **Selector direction is trigger-dependent.** `@Attacker`/`@Victim` resolve
  differently on attack vs defence; use `Mappings#foe`/`target`/`aeSelector`,
  never a hardcoded selector.
- **Reuse the alias maps and `ParamSpec`, don't fork them.** The migrator and the
  live compiler share both so a migrated config typechecks exactly as a
  hand-authored one would.
- **Genuinely exotic mechanics stay honest TODOs.** Some legacy effects have no
  SE equivalent at all; the enchant loads without them rather than shipping a
  wrong approximation.
