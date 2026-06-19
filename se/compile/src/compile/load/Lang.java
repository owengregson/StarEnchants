package compile.load;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import schema.diag.Diagnostic;

/**
 * The compiled snapshot of {@code lang.yml} (docs/v3-directives.md §L) — every player-facing message, keyed
 * by a namespaced dotted key ({@code command.give.book}, {@code apply.hold-item}, {@code menu.enchanter.bought}).
 * A parallel immutable reference to the content {@link Library}, swapped in the same atomic {@code /se reload}
 * transaction; readers look up a key and substitute {@code {TOKEN}} placeholders.
 *
 * <p>Pure (no Bukkit): {@link #format}/{@link #lines} return the raw legacy {@code &}-coded template with
 * placeholders filled — the {@code &}→{@code §} colour translation happens at the Bukkit send boundary (the
 * {@code item.lang.Messages} facade), since {@code compile} carries no server API. A missing key renders as a
 * visible {@code &c<key>?} marker (never an exception, never a silent blank) so a typo surfaces in-game.
 *
 * <p>{@link #defaults()} is the shipped English catalogue; {@code lang.yml} overrides any subset of it. Two
 * maps: {@link #singles} for one-line messages, {@link #lists} for multi-line blocks (the {@code /se} usage
 * help, the migrate usage). The built-in defaults are byte-identical (after {@code &}→{@code §}) to the
 * literals they replaced, so message-asserting tests keep passing.
 *
 * @param singles     single-line message templates by key
 * @param lists       multi-line message blocks by key
 * @param diagnostics every diagnostic raised loading {@code lang.yml}
 */
public record Lang(Map<String, String> singles, Map<String, List<String>> lists, List<Diagnostic> diagnostics) {

    public Lang {
        singles = Map.copyOf(singles);
        lists = deepCopy(lists);
        diagnostics = List.copyOf(diagnostics);
    }

    /** An empty catalogue (every lookup is the missing-key marker) — used only as a degenerate fallback. */
    public static Lang empty() {
        return new Lang(Map.of(), Map.of(), List.of());
    }

    /** Whether any blocking diagnostic was raised loading {@code lang.yml}. */
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(Diagnostic::blocking);
    }

    /**
     * The {@code &}-coded template for {@code key} with {@code {TOKEN}} placeholders substituted from
     * {@code kv} (alternating {@code "TOKEN", value, …}). An unknown key returns {@code &c<key>?}.
     */
    public String format(String key, Object... kv) {
        String template = singles.get(key);
        if (template == null) {
            return "&c" + key + "?"; // visible missing-key marker
        }
        return substitute(template, kv);
    }

    /** The multi-line block for {@code key} with placeholders substituted; an unknown key is a one-line marker. */
    public List<String> lines(String key, Object... kv) {
        List<String> template = lists.get(key);
        if (template == null) {
            return List.of("&c" + key + "?");
        }
        List<String> out = new java.util.ArrayList<>(template.size());
        for (String line : template) {
            out.add(substitute(line, kv));
        }
        return out;
    }

    /** Whether {@code key} is a known single-line message (for callers that branch on presence). */
    public boolean has(String key) {
        return singles.containsKey(key);
    }

    private static String substitute(String template, Object... kv) {
        if (kv.length == 0 || template.indexOf('{') < 0) {
            return template;
        }
        String out = template;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            out = out.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return out;
    }

    private static Map<String, List<String>> deepCopy(Map<String, List<String>> in) {
        Map<String, List<String>> out = new LinkedHashMap<>(in.size());
        in.forEach((k, v) -> out.put(k, List.copyOf(v)));
        return Map.copyOf(out);
    }

    /** The shipped English catalogue — the single source of default message text. */
    public static Lang defaults() {
        Map<String, String> s = new LinkedHashMap<>();

        // ── /se command (SeCommand) ──────────────────────────────────────────────────────────────
        s.put("command.not-a-player", "&cThat command can only be run by a player.");
        s.put("command.give.gem", "&aSoul gem minted. &7Right-click it (or /se soulmode) to toggle soul mode.");
        s.put("command.soul.no-gem", "&cHold a soul gem first (/se gem).");
        s.put("command.soul.split-usage", "&cUsage: /se split <amount> &7— hold the gem to split.");
        s.put("command.soul.split-ok",
                "&aSplit off &f{MOVED}&a souls into a new gem &7(this gem keeps {REMAINING}).");
        s.put("command.soul.split-bad", "&cThe amount must be a positive number.");
        s.put("command.soul.split-too-many",
                "&cThis gem has only &f{REMAINING}&c souls — leave at least one behind.");
        s.put("command.error.bad-number", "&cThat is not a number: &7{ARG}");
        s.put("command.error.bad-level", "&cLevel must be a number, got &f{ARG}");
        s.put("command.error.level-range", "&cLevel must be 1–{MAX} for &f{KEY}");
        s.put("command.error.no-such-enchant", "&cNo such enchant: &f{KEY}");
        s.put("command.error.no-such-crystal", "&cNo such crystal: &f{KEY}");
        s.put("command.error.no-such-tier", "&cNo such tier: &f{TIER}");
        s.put("command.reload.start", "&7StarEnchants: {MODE} content…");
        s.put("command.reload.loaded", "&aStarEnchants: {VERB} {COUNT} abilities (generation {GEN}).");
        s.put("command.reload.errors-header", "&cStarEnchants: {N} error(s) — kept the previous content:");
        s.put("command.reload.error-line", "&c  {DIAGNOSTIC}");
        s.put("command.menu.unknown", "&cUnknown menu '{NAME}'. &7Available: {AVAILABLE}");
        s.put("command.menu.no-permission", "&cYou don't have permission to open that menu.");
        s.put("command.migrate.start", "&7StarEnchants: migrating {FORMAT} from &f{SOURCE}&7…");
        s.put("command.migrate.unknown-format", "&cUnknown format '{FORMAT}' — use &fee&c, &fea&c or &fae&c.");
        s.put("command.migrate.done",
                "&aMigrated {N} file(s): &f{WRITTEN}&a written, &f{SKIPPED}&a already present; "
                        + "&f{NOTES}&a review note(s).");
        s.put("command.migrate.review",
                "&7Review the &f# TODO &7markers in &f{TARGET}&7, then move files into content/.");
        s.put("command.migrate.failed", "&cMigration failed reading &f{SOURCE}&c: {ERROR}");
        s.put("command.enchant.usage", "&eUsage: /se enchant <key> [level]");
        s.put("command.crystal.usage", "&eUsage: /se crystal <key> &7— a crystal you drag onto gear to apply");
        s.put("command.unopened.usage", "&eUsage: /se unopened <tier> &7— right-click it to reveal a random book");
        s.put("command.book.usage", "&eUsage: /se book <enchant> [level] &7— a book you drag onto gear to apply it");
        s.put("command.give.crystal", "&aMinted a crystal: &f{KEY}&a. &7Drag it onto gear to apply.");
        s.put("command.give.extractor", "&aMinted a crystal extractor. &7Drag it onto crystal-bearing gear to extract.");
        s.put("command.give.heroic", "&6Minted a heroic upgrade. &7Drag it onto armour or a weapon to attempt.");
        s.put("command.give.slot", "&5Minted a {KIND}. &7Drag it onto gear to add enchant slots.");
        s.put("command.give.transmog", "&5Minted a transmog scroll. &7Drag it onto enchanted gear.");
        s.put("command.give.holy", "&fMinted a holy scroll. &7Carry it to survive a death once.");
        s.put("command.give.nametag", "&bMinted an item nametag. &7Drag it onto gear, then type the new name.");
        s.put("command.give.blackscroll",
                "&8Minted a black scroll. &7Drag it onto enchanted gear to extract an enchant.");
        s.put("command.give.randomizer",
                "&eMinted a randomizer scroll. &7Drag it onto an enchant book to reroll its success.");
        s.put("command.give.unopened",
                "&bMinted an unopened &f{TIER} &bbook. &7Right-click it to reveal a random enchant book.");
        s.put("command.give.book", "&aMinted an enchant book for &f{KEY} &7(level {LEVEL})&a.");
        s.put("command.reference.header", "&e{CATEGORY} &7({COUNT}): &f{ITEMS}");
        s.put("command.reference.list", "&eReference: &f{CATEGORIES} &7— /se effects|selectors|triggers|conditions|variables");
        // §J give-to-player surface + the inverse removeenchant
        s.put("command.give.usage",
                "&eUsage: /se give <type> <player> [args] &7— type: gem [amount] | crystal <key> | book <enchant> "
                        + "[level] [success] | item <id> [args] | heroic | upgrade | orb | slotgem | blackscroll | "
                        + "randomizer | transmog | holy | nametag | unopened <tier>");
        s.put("command.give.delivered", "&aGave &f{ITEM}&a to &f{PLAYER}&a.");
        s.put("command.give.item", "&aReceived &f{ID}&a.");
        s.put("command.give.set-unavailable",
                "&cGiving set pieces is not available yet — no set-piece model exists (tracked follow-up).");
        s.put("command.error.no-such-player", "&cNo online player named &f{PLAYER}&c.");
        s.put("command.error.no-such-item", "&cNo such item: &f{ID}&c.");
        s.put("command.removeenchant.usage", "&eUsage: /se removeenchant <enchant> &7— strips it from the held item");

        // ── ItemEnchanter ApplyResult reasons ─────────────────────────────────────────────────────
        s.put("apply.no-such-enchant", "&cNo such enchant: &f{KEY}");
        s.put("apply.level-range", "&cLevel must be 1–{MAX} for &f{KEY}");
        s.put("apply.level-undefined", "&cLevel {LEVEL} of &f{KEY} &cis not defined.");
        s.put("apply.not-applicable", "&c{DISPLAY} &ccannot be applied to that item.");
        s.put("apply.ok", "&a{DISPLAY}");
        s.put("apply.no-enchant-slots", "&cThis item has no free enchant slots ({MAX} max).");
        s.put("apply.requires", "&cRequires &f{REQ} &cfirst.");
        s.put("apply.requires-level", "&cRequires &f{REQ} &cat level {LEVEL} or higher.");
        s.put("apply.conflicts", "&c{DISPLAY} &ccannot be combined with &f{OTHER}&c.");
        s.put("apply.applied-suffix", "{MSG} &7applied (level {LEVEL}).");
        s.put("apply.hold-item", "&cHold an item first.");
        s.put("apply.removed", "&aRemoved &f{KEY}&a from the held item.");
        s.put("apply.not-present", "&cThe held item does not carry &f{KEY}&c.");
        s.put("apply.crystal.no-such", "&cNo such crystal: &f{KEY}");
        s.put("apply.crystal.no-compile", "&c{KEY} &cdid not compile.");
        s.put("apply.crystal.on-item", "&cApply the crystal onto an item.");
        s.put("apply.crystal.single-item", "&cApply the crystal to a single item — split the stack first.");
        s.put("apply.crystal.not-crystal", "&cThat is not a crystal.");
        s.put("apply.crystal.no-slots", "&cThis item has no free crystal slots ({MAX} max).");
        s.put("apply.crystal.max-reached", "&cThis item already holds the maximum {MAX} crystals.");
        s.put("apply.crystal.applied", "{LABEL} &7crystal applied.");
        s.put("apply.crystal.none", "&cThat item carries no crystal to extract.");
        s.put("apply.crystal.extracted", "&aExtracted the crystal.");

        // ── Hardcoded gesture/service guards (no items/ home; not the config-backed *Config messages) ──
        s.put("common.single-item", "&cApply to a single item — split the stack first.");
        s.put("crystal.merge-single", "&cMerge onto a single crystal — split the stack first.");
        s.put("crystal.merge-pairs", "&cMulti-crystals are pairs — you cannot merge a multi-crystal further.");
        s.put("heroic.not-gear", "&cApply the heroic upgrade onto a piece of gear.");
        s.put("heroic.already-heroic", "&7That item is already heroic.");
        s.put("slot.not-gear", "&cApply the slot item onto a piece of gear.");
        s.put("slot.not-slot-item", "&cThat is not a slot item.");
        s.put("scroll.black.apply-target", "&cApply the black scroll onto enchanted gear.");
        s.put("scroll.randomizer.apply-target", "&cApply the randomizer onto an enchant book.");
        s.put("scroll.transmog.apply-target", "&cApply the transmog scroll onto enchanted gear.");
        s.put("scroll.nametag.busy", "&7Finish your current rename first, or type 'cancel'.");
        s.put("scroll.nametag.target-gone",
                "&cThe item to rename is no longer there — your nametag was returned.");
        s.put("scroll.nametag.cannot-rename", "&cThat item cannot be renamed — your nametag was returned.");

        // ── Menu chat feedback (the GUI's chat replies; titles/labels are menu LAYOUT, see menus/) ──
        s.put("menu.alchemist.bad-input", "&cPlace a single enchant book in each slot.");
        s.put("menu.alchemist.cant-combine",
                "&cThose books can't be combined — they must be the same enchant and level (below max).");
        s.put("menu.alchemist.combined", "&aCombined into a higher-level book!");
        s.put("menu.tinkerer.bad-input", "&cPlace a single enchant book to salvage.");
        s.put("menu.tinkerer.not-book", "&cThat isn't an enchant book.");
        s.put("menu.tinkerer.salvaged", "&aSalvaged for &a{LEVELS} &alevel{S}.");
        s.put("menu.enchanter.too-poor", "&cYou need &a{COST} &clevels to buy that.");
        s.put("menu.enchanter.bought", "&aBought a &f{TIER} &amystery book.");
        s.put("menu.admin.granted", "&aGranted a guaranteed &f{DISPLAY} &abook.");

        // ── Config-backed item messages (were items/*.yml message-* fields; now centralised here) ──
        s.put("soul.activate", "&aSoul mode &lON&a.");
        s.put("soul.deactivate", "&7Soul mode &lOFF&7.");
        s.put("soul.soul-use", "&7Souls remaining: &a{AMOUNT}");
        s.put("crystal.apply-success", "&aApplied &f{CRYSTAL}&a to your item.");
        s.put("crystal.apply-fail", "&cThe crystal shattered without taking hold.");
        s.put("crystal.no-slots", "&cThat item has no free crystal slot.");
        s.put("crystal.merge", "&dMerged into a multi-crystal: &f{CRYSTAL}&d.");
        s.put("crystal.extract-success", "&aExtracted &f{CRYSTAL}&a back into a crystal.");
        s.put("heroic.success", "&6Heroic upgrade succeeded! &7Your gear is now &6heroic&7.");
        s.put("heroic.fail", "&cThe heroic upgrade failed — the upgrade was consumed.");
        s.put("slot.apply", "&aSlots increased — this item now has &f{SLOTS}&a total.");
        s.put("slot.at-cap", "&cThat item is already at the maximum slots.");
        s.put("scroll.black.success", "&aExtracted &f{ENCHANT}&a into a book.");
        s.put("scroll.black.fail", "&cThe black scroll crumbled — nothing was extracted.");
        s.put("scroll.black.no-enchants", "&cThat item has no enchants to extract.");
        s.put("scroll.randomizer.success", "&aThe book's success chance was rerolled to &f{PERCENT}%&a.");
        s.put("scroll.randomizer.not-book", "&cThe randomizer only works on an enchant book.");
        s.put("scroll.randomizer.single-book", "&cApply to a single book — split the stack first.");
        s.put("scroll.transmog.success", "&aReordered the enchant display.");
        s.put("scroll.transmog.no-enchants", "&cThat item has no enchants to transmog.");
        s.put("scroll.holy.saved", "&fThe holy scroll shattered — your items were spared.");
        s.put("scroll.nametag.prompt", "&7Type the new item name in chat (or 'cancel').");
        s.put("scroll.nametag.renamed", "&aRenamed your item.");
        s.put("scroll.nametag.blacklisted", "&cThat name contains a blacklisted word.");
        s.put("scroll.nametag.cancelled", "&7Rename cancelled.");
        s.put("book.unopened.open", "&aYou revealed &f{ENCHANT} {LEVEL}&a (&f{PERCENT}%&a success)!");
        s.put("book.unopened.empty-tier", "&cThere are no enchants in that tier to reveal.");

        // ── Multi-line blocks ──────────────────────────────────────────────────────────────────────
        Map<String, List<String>> l = new LinkedHashMap<>();
        l.put("command.usage", List.of(
                "&eStarEnchants commands:",
                "&e  /se reload [--dry-run] &7— rebuild content",
                "&e  /se give <type> <player> [args] &7— give any item to a player (gem|crystal|book|item|set|heroic|…)",
                "&e  /se enchant <key> [level] &7— apply an enchant to the held item",
                "&e  /se removeenchant <key> &7— strip an enchant from the held item",
                "&e  /se crystal <key> &7— mint a crystal item (drag it onto gear to apply)",
                "&e  /se heroic &7— mint a heroic upgrade (drag it onto armour/weapon)",
                "&e  /se orb &7— mint a slot expander (drag onto gear for +N slots)",
                "&e  /se slotgem &7— mint a slot gem (drag onto gear for +1 slot)",
                "&e  /se blackscroll &7— mint a black scroll (extract an enchant into a book)",
                "&e  /se randomizer &7— mint a randomizer scroll (reroll a book's success)",
                "&e  /se transmog &7— mint a transmog scroll (reorder enchant lore)",
                "&e  /se holy &7— mint a holy scroll (survive a death once)",
                "&e  /se nametag &7— mint an item nametag (rename gear via chat)",
                "&e  /se unopened <tier> &7— mint an unopened book (right-click to reveal)",
                "&e  /se menu [name] &7— open a GUI (default: the enchant-application menu)",
                "&e  /se effects|selectors|triggers|conditions|variables|list &7— browse the DSL reference",
                "&e  /se gem &7— mint a soul gem (right-click it to toggle soul mode)",
                "&e  /se soulmode &7— toggle soul mode for the held gem",
                "&e  /se split <amount> &7— split souls off the held gem into a new gem",
                "&e  /se migrate <ee|ea|ae> <path> &7— import legacy EE/EA/AdvancedEnchantments configs"));
        l.put("command.migrate.usage", List.of(
                "&eUsage: /se migrate <ee|ea|ae> <sourcePath>",
                "&7  ee &8— path to EliteEnchantments' enchantments.yml",
                "&7  ea &8— path to EliteArmor's armor/ directory",
                "&7  ae &8— path to AdvancedEnchantments' enchantments.yml"));

        return new Lang(s, l, List.of());
    }
}
