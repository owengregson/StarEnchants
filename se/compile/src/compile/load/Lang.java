package compile.load;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import schema.diag.Diagnostic;

/**
 * Compiled snapshot of {@code lang.yml} (§L) — player-facing messages keyed by dotted key, swapped in the
 * same atomic {@code /se reload} transaction as content. {@code &}→{@code §} translation happens at the
 * Bukkit send boundary, not here. A missing key renders as a visible {@code &c<key>?} marker (never an
 * exception, never a silent blank) so a typo surfaces in-game; {@code lang.yml} overrides any subset of
 * {@link #defaults()}.
 */
public record Lang(Map<String, String> singles, Map<String, List<String>> lists, List<Diagnostic> diagnostics) {

    public Lang {
        singles = Map.copyOf(singles);
        lists = deepCopy(lists);
        diagnostics = List.copyOf(diagnostics);
    }

    /** An empty catalogue (every lookup is the missing-key marker) — the degenerate fallback. */
    public static Lang empty() {
        return new Lang(Map.of(), Map.of(), List.of());
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(Diagnostic::blocking);
    }

    /**
     * The template for {@code key} with {@code {TOKEN}} placeholders substituted from {@code kv} (alternating
     * {@code "TOKEN", value, …}). An unknown key returns the {@code &c<key>?} marker.
     */
    public String format(String key, Object... kv) {
        String template = singles.get(key);
        if (template == null) {
            return "&c" + key + "?";
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

    /** Whether {@code key} is a known single-line message. */
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

    /** The shipped English catalogue. */
    public static Lang defaults() {
        Map<String, String> s = new LinkedHashMap<>();

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
        s.put("command.import.start", "&7StarEnchants: validating import &f{KEY}&7…");
        s.put("command.import.bad-code", "&cThat is not a valid import code: &f{ERROR}");
        s.put("command.import.invalid",
                "&cImport &f{KEY}&c has {N} error(s) — nothing written:");
        s.put("command.import.write-failed", "&cCould not write &f{KEY}&c: {ERROR}");
        s.put("command.import.done", "&aImported enchant &f{KEY}&a ({LEVELS} level(s)) and reloaded.");
        s.put("command.pack.empty", "&7No config packs found. Create one with &f/se pack export <name>&7.");
        s.put("command.pack.list-header", "&eConfig packs &7({COUNT}):");
        s.put("command.pack.list-entry", "&e  {NAME} &7— {DESC} &8({FILES} files)");
        s.put("command.pack.info",
                "&ePack &f{NAME}&e: &7{DESC} &8| author {AUTHOR} | created {CREATED} | {FILES} files");
        s.put("command.pack.unknown", "&cNo such pack: &f{NAME}&c. Use &f/se pack list&c.");
        s.put("command.pack.bad-name",
                "&cInvalid pack name &f{NAME}&c — use letters, digits, &7_&c and &7-&c only.");
        s.put("command.pack.apply-start", "&7Applying config pack &f{NAME}&7…");
        s.put("command.pack.apply-done",
                "&aApplied pack &f{NAME}&a ({FILES} files). &7Previous config backed up as &f{BACKUP}&7.");
        s.put("command.pack.apply-skipped",
                "&e{N} pack entr(ies) were outside the config surface and were skipped.");
        s.put("command.pack.apply-note",
                "&7Config swapped + reloaded. &8Toggled features/integrations need a server restart.");
        s.put("command.pack.export-done", "&aExported the current config as pack &f{NAME}&a ({FILES} files).");
        s.put("command.pack.error", "&cPack operation failed: &f{ERROR}");
        s.put("command.enchant.usage", "&eUsage: /se enchant <key> [level]");
        s.put("command.crystal.usage", "&eUsage: /se crystal <key> &7— a crystal you drag onto gear to apply");
        s.put("command.unopened.usage", "&eUsage: /se unopened <tier> &7— right-click it to reveal a random book");
        s.put("command.book.usage", "&eUsage: /se book <enchant> [level] &7— a book you drag onto gear to apply it");
        s.put("command.give.crystal", "&aMinted a crystal: &f{KEY}&a. &7Drag it onto gear to apply.");
        s.put("command.give.extractor", "&aMinted a crystal extractor. &7Drag it onto crystal-bearing gear to extract.");
        s.put("command.give.heroic", "&6Minted a heroic upgrade. &7Drag it onto armour or a weapon to attempt.");
        s.put("command.give.slot", "&5Minted a {KIND}. &7Drag it onto gear to add enchant slots.");
        s.put("command.give.transmog", "&5Minted a transmog scroll. &7Drag it onto enchanted gear.");
        s.put("command.give.godlytransmog", "&5Minted a godly transmog. &7Drag it onto enchanted gear to reorder.");
        s.put("command.give.holy", "&fMinted a holy white scroll. &7Carry it to survive a death once.");
        s.put("command.give.nametag", "&bMinted an item nametag. &7Drag it onto gear, then type the new name.");
        s.put("command.give.trak", "&aMinted a trak gem. &7Drag it onto eligible gear to reveal its lifetime count.");
        s.put("command.give.blackscroll",
                "&8Minted a black scroll. &7Drag it onto enchanted gear to extract an enchant.");
        s.put("command.give.randomizer",
                "&eMinted a randomizer scroll. &7Drag it onto an enchant book to reroll its success.");
        s.put("command.give.dust", "&aMinted success dust. &7Drag it onto an enchant book to boost its success.");
        s.put("command.give.whitescroll",
                "&fMinted a white scroll. &7Drag it onto gear to protect it from a failed enchant.");
        s.put("command.give.unopened",
                "&bMinted an unopened &f{TIER} &bbook. &7Right-click it to reveal a random enchant book.");
        s.put("command.give.book", "&aMinted an enchant book for &f{KEY} &7(level {LEVEL})&a.");
        s.put("command.reference.header", "&e{CATEGORY} &7({COUNT}): &f{ITEMS}");
        s.put("command.reference.list", "&eReference: &f{CATEGORIES} &7— /se effects|selectors|triggers|conditions|variables");
        s.put("command.give.usage",
                "&eUsage: /se give <type> <player> [args] &7— type: gem [amount] | crystal <key> | book <enchant> "
                        + "[level] [success] | heroic | upgrade | orb | blackscroll | randomizer | transmog | "
                        + "godlytransmog | holy | nametag | dust [percent] | whitescroll | unopened <tier>");
        s.put("command.give.delivered", "&aGave &f{ITEM}&a to &f{PLAYER}&a.");
        s.put("command.give.set", "&aMinted the &f{KEY}&a {PIECE} piece. &7Wear the set to complete its bonus.");
        s.put("command.give.set-piece",
                "&cThe &f{KEY}&c set has no &f{PIECE}&c piece — use HELMET / CHESTPLATE / LEGGINGS / BOOTS.");
        s.put("command.set.usage", "&eUsage: /se give set <player> <set> <piece> &7— mint an armour set piece");
        s.put("command.error.no-such-set", "&cNo such set: &f{KEY}");
        s.put("command.error.no-such-player", "&cNo online player named &f{PLAYER}&c.");
        s.put("command.removeenchant.usage", "&eUsage: /se removeenchant <enchant> &7— strips it from the held item");

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

        // Hardcoded gesture/service guards: no items/ home, not the config-backed *Config messages.
        s.put("common.single-item", "&cApply to a single item — split the stack first.");
        s.put("common.wrong-applies", "&cThis can only be applied to: &f{KINDS}&c.");
        s.put("crystal.merge-single", "&cMerge onto a single crystal — split the stack first.");
        s.put("crystal.merge-cap", "&cThat multi-crystal is full — a crystal holds at most {MAX} effects.");
        s.put("crystal.extract-not-multi", "&cThat is a single crystal — apply the extractor to a multi-crystal or to gear.");
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

        // Menu chat replies only; menu titles/labels are LAYOUT (menus/), not here.
        s.put("menu.alchemist.bad-input", "&cPlace a single enchant book in each slot.");
        s.put("menu.alchemist.cant-combine",
                "&cThose books can't be combined — they must be the same enchant and level (below max).");
        s.put("menu.alchemist.combined", "&aCombined into a higher-level book!");
        s.put("menu.tinkerer.bad-input", "&cPlace a single enchant book to salvage.");
        s.put("menu.tinkerer.not-book", "&cThat isn't an enchant book.");
        s.put("menu.tinkerer.salvaged", "&aSalvaged for &a{LEVELS} &alevel{S}.");
        s.put("menu.enchanter.too-poor", "&cYou need &a{COST} &clevels to buy that.");
        s.put("menu.enchanter.bought", "&aBought a &f{TIER} &amystery book.");
        s.put("menu.admin.granted", "&aGranted a guaranteed &f{DISPLAY} &alevel &f{LEVEL} &abook.");

        // Config-backed item messages (centralised here, not in items/*.yml). soul.activate / soul.deactivate /
        // soul.empty are multi-line blocks (see the lists map `l` below).
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
        s.put("slot.fail", "&eThe orb failed — no slots were added.");
        s.put("scroll.black.success", "&aExtracted &f{ENCHANT}&a into a book.");
        s.put("scroll.black.fail", "&cThe black scroll crumbled — nothing was extracted.");
        s.put("scroll.black.no-enchants", "&cThat item has no enchants to extract.");
        s.put("scroll.randomizer.success", "&aThe book's success chance was rerolled to &f{PERCENT}%&a.");
        s.put("scroll.randomizer.not-book", "&cThe randomizer only works on an enchant book.");
        s.put("scroll.randomizer.single-book", "&cApply to a single book — split the stack first.");
        s.put("scroll.transmog.success", "&aReordered the enchant display.");
        s.put("scroll.transmog.no-enchants", "&cThat item has no enchants to transmog.");
        s.put("scroll.holy.applied", "&6Holy protection applied — this item will survive your death once.");
        s.put("scroll.holy.apply-target", "&cApply the holy white scroll onto an item.");
        s.put("scroll.holy.already", "&7That item is already holy-protected.");
        s.put("scroll.holy.occupied", "&cThat item already has another applied item — only one fits.");
        s.put("scroll.holy.fail", "&eThe holy scroll failed — the item is not protected.");
        s.put("scroll.holy.kept", "&6Your holy-protected item(s) survived your death — &f{AMOUNT}&6 kept.");
        s.put("scroll.nametag.prompt", "&7Type the new item name in chat (or 'cancel').");
        s.put("scroll.nametag.gui-title", "&8Rename — & colours work");
        s.put("scroll.nametag.renamed", "&aRenamed your item.");
        s.put("scroll.nametag.blacklisted", "&cThat name contains a blacklisted word.");
        s.put("scroll.nametag.cancelled", "&7Rename cancelled.");
        s.put("trak.applied", "&aTrak applied — this item now shows its lifetime count.");
        s.put("trak.apply-target", "&cApply the trak gem onto an item.");
        s.put("trak.wrong-kind", "&cThat gem only applies to: &f{KINDS}&c.");
        s.put("trak.already", "&7That item already has this trak.");
        s.put("trak.occupied", "&cThat item already has another applied item — only one fits.");
        s.put("book.unopened.open", "&aYou revealed &f{ENCHANT} {LEVEL}&a (&f{PERCENT}%&a success)!");
        s.put("book.unopened.empty-tier", "&cThere are no enchants in that tier to reveal.");

        Map<String, List<String>> l = new LinkedHashMap<>();
        // Soul-mode feedback (multi-line). soul.empty shows when /se soulmode is run with no gem held.
        l.put("soul.activate", List.of(
                " ",
                "&a&l** SOUL MODE: &nON&r&a&l **",
                "&7Your soul enchantments will now drain souls.",
                " "));
        l.put("soul.deactivate", List.of(
                " ",
                "&c&l** SOUL MODE: &nOFF&r&c&l **",
                "&7Your soul enchantments will no longer drain souls.",
                " "));
        l.put("soul.empty", List.of(
                " ",
                "&c&l** SOUL MODE: &nOFF&r&c&l **",
                "&7You have no soul gems left!",
                " "));
        l.put("command.usage", List.of(
                "&eStarEnchants commands:",
                "&e  /se reload [--dry-run] &7— rebuild content",
                "&e  /se give <type> <player> [args] &7— give any item to a player (gem|crystal|book|item|set|heroic|…)",
                "&e  /se enchant <key> [level] &7— apply an enchant to the held item",
                "&e  /se removeenchant <key> &7— strip an enchant from the held item",
                "&e  /se crystal <key> &7— mint a crystal item (drag it onto gear to apply)",
                "&e  /se heroic &7— mint a heroic upgrade (drag it onto armour/weapon)",
                "&e  /se orb &7— mint a slot expander (drag onto gear for +N slots)",
                "&e  /se blackscroll &7— mint a black scroll (extract an enchant into a book)",
                "&e  /se randomizer &7— mint a randomizer scroll (reroll a book's success)",
                "&e  /se transmog &7— mint a transmog scroll (reorder enchant lore)",
                "&e  /se godlytransmog &7— mint a godly transmog (hand-reorder enchant lore)",
                "&e  /se holy &7— mint a holy white scroll (survive a death once)",
                "&e  /se nametag &7— mint an item nametag (rename gear via chat)",
                "&e  /se dust [percent] &7— mint success dust (random, or a fixed % to boost a book)",
                "&e  /se whitescroll &7— mint a white scroll (protect gear from a failed enchant)",
                "&e  /se unopened <tier> &7— mint an unopened book (right-click to reveal)",
                "&e  /se menu [name] &7— open a GUI (default: the enchant-application menu)",
                "&e  /se effects|selectors|triggers|conditions|variables|list &7— browse the DSL reference",
                "&e  /se gem &7— mint a soul gem (right-click it to toggle soul mode)",
                "&e  /se soulmode &7— toggle soul mode for the held gem",
                "&e  /se split <amount> &7— split souls off the held gem into a new gem",
                "&e  /se migrate <ee|ea|ae> <path> &7— import legacy EE/EA/AdvancedEnchantments configs",
                "&e  /se import <code> &7— apply an enchant from a web-creator SE1 code",
                "&e  /se pack <list|info|apply|export> &7— manage config packs (swap the whole config)"));
        l.put("command.migrate.usage", List.of(
                "&eUsage: /se migrate <ee|ea|ae> <sourcePath>",
                "&7  ee &8— path to EliteEnchantments' enchantments.yml",
                "&7  ea &8— path to EliteArmor's armor/ directory",
                "&7  ae &8— path to AdvancedEnchantments' enchantments.yml"));
        l.put("command.import.usage", List.of(
                "&eUsage: /se import <code>",
                "&7  paste an &fSE1:&7 code from the web enchant creator to apply it live"));
        l.put("command.pack.usage", List.of(
                "&eUsage: /se pack <action>",
                "&7  list &8— show every available config pack",
                "&7  info <name> &8— show a pack's details",
                "&7  apply <name> &8— back up the current config, swap in the pack, and reload",
                "&7  export <name> [description] &8— save the current config as a new pack"));

        return new Lang(s, l, List.of());
    }
}
