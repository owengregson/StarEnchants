package imagegen.imports;

import compile.load.Library;
import compile.load.LibraryLoader;
import compile.load.SetDef;
import compile.load.TierRegistry;
import engine.boot.ContentCompiler;
import imagegen.fixture.ItemFixture;
import item.codec.CombatState;
import item.codec.HeroicStat;
import item.codec.PdcItemStateStore;
import item.render.LoreRenderer;
import item.render.LoreStyle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Imports item tooltips from a real plugin content tree, rendered exactly as the game mints them — the
 * maintainable alternative to hand-transcribed fixtures. Each manifest source is compiled off-server through
 * the plugin's own {@link LibraryLoader} (the {@code validateContent} path), and each selected set's pieces
 * are rendered through the plugin's own {@link LoreRenderer}, so a preview cannot drift from what the live
 * plugin draws. Vanilla enchants — which the client draws, not the renderer — are prepended via
 * {@link VanillaEnchantLore} so the preview matches what a player actually sees.
 *
 * <p>Mirrors {@code feature.apply.ItemEnchanter.mintSetPiece}: a member's name is its own or the set display,
 * its combat state carries the set's <em>custom</em> enchants plus the set key (armour) or weapon key
 * (weapon), and vanilla enchant names render as gray lines above the authored lore.
 */
public final class PackImporter {

    private PackImporter() {
    }

    private static final String SET_PREFIX = "sets/";

    /** Compile every manifest source and flatten its selected sets into render-ready fixtures. */
    public static List<ItemFixture> fixtures(ImportManifest manifest) {
        List<ItemFixture> out = new ArrayList<>();
        for (ImportManifest.Source source : manifest.sources()) {
            out.addAll(fixturesFor(source));
        }
        return out;
    }

    private static List<ItemFixture> fixturesFor(ImportManifest.Source source) {
        Path root = Path.of(source.root());
        Library library = LibraryLoader.load(root, ContentCompiler.production(new StubResolvers()), 1);
        System.out.println("[imagegen] imported " + source.root() + ": " + library.sets().size()
                + " sets, " + library.diagnostics().size() + " diagnostics");
        LoreRenderer renderer = renderer(library);
        List<ItemFixture> out = new ArrayList<>();
        for (SetDef def : selected(library, source)) {
            out.addAll(pieces(library, renderer, def));
        }
        return out;
    }

    /** The sets a source asks for: every set for {@code "*"}, else each named key (skipping unknowns). */
    private static List<SetDef> selected(Library library, ImportManifest.Source source) {
        if (source.allSets()) {
            return library.sets();
        }
        List<SetDef> out = new ArrayList<>();
        for (String key : source.setKeys()) {
            String stableKey = key.startsWith(SET_PREFIX) ? key : SET_PREFIX + key;
            SetDef def = library.setDefOf(stableKey);
            if (def == null) {
                System.out.println("[imagegen] no such set '" + key + "' in " + source.root() + " — skipping");
            } else {
                out.add(def);
            }
        }
        return out;
    }

    /** The four armour pieces, plus the weapon when the set has one. */
    private static List<ItemFixture> pieces(Library library, LoreRenderer renderer, SetDef def) {
        String simpleKey = def.key().startsWith(SET_PREFIX) ? def.key().substring(SET_PREFIX.length()) : def.key();
        List<ItemFixture> out = new ArrayList<>();
        for (SetDef.Member member : def.armorMembers()) {
            Map<String, compile.load.EnchantRoll> roster = def.armorEnchantsFor(member.slot());
            CombatState state = new CombatState(custom(library, roster), List.of(), def.key(), false);
            out.add(fixture("set-" + simpleKey + "-" + member.slot(), member, def,
                    vanilla(library, roster), renderer, state));
        }
        if (def.hasWeapon()) {
            CombatState state = new CombatState(custom(library, def.weaponEnchants()), List.of(), null, def.key(),
                    false, HeroicStat.NONE, 0, null, null);
            out.add(fixture("set-" + simpleKey + "-weapon", def.weapon(), def,
                    vanilla(library, def.weaponEnchants()), renderer, state));
        }
        return out;
    }

    /** Game order: the client-drawn vanilla enchant lines, then the renderer's authored set/enchant lore. */
    private static ItemFixture fixture(String id, SetDef.Member member, SetDef def,
            Map<String, Integer> vanillaEnchants, LoreRenderer renderer, CombatState state) {
        String name = member.name() != null ? member.name() : def.display();
        List<String> lore = new ArrayList<>();
        vanillaEnchants.forEach((enchant, level) -> lore.add(VanillaEnchantLore.line(enchant, level)));
        lore.addAll(renderer.lines(state, member.slot()));
        return new ItemFixture(id, member.material(), name, lore);
    }

    /**
     * The set's custom plugin enchants ({@code enchants/<id> → level}) — those the library has a def for, so
     * the renderer can name them. Identifying custom enchants by library membership (not a key-prefix guess)
     * keeps a vanilla name out of the combat state, where it would render as the unknown-enchant label.
     */
    private static Map<String, Integer> custom(Library library, Map<String, compile.load.EnchantRoll> configured) {
        Map<String, Integer> out = new LinkedHashMap<>();
        configured.forEach((ref, roll) -> {
            if (library.displayNameOf(ref) != null) {
                out.put(ref, level(roll));
            }
        });
        return out;
    }

    /** The set's vanilla enchants (everything the library has no def for) — drawn by {@link VanillaEnchantLore}. */
    private static Map<String, Integer> vanilla(Library library, Map<String, compile.load.EnchantRoll> configured) {
        Map<String, Integer> out = new LinkedHashMap<>();
        configured.forEach((ref, roll) -> {
            if (library.displayNameOf(ref) == null) {
                out.put(ref, level(roll));
            }
        });
        return out;
    }

    /**
     * The level a fixture draws an entry at: the TOP of its band, always. Generated art must not depend on a
     * roll — a chance-gated or banded entry would otherwise redraw every image differently each run, and the
     * top of the band is the piece the catalogue is meant to advertise.
     */
    private static int level(compile.load.EnchantRoll roll) {
        return roll.max();
    }

    /** A library-backed renderer mirroring {@code StarEnchantsPlugin}'s cold-apply wiring (names, tier colours, set lore). */
    private static LoreRenderer renderer(Library library) {
        return new LoreRenderer(LoreRenderer.Config
                .of(() -> LoreStyle.DEFAULT, library::displayNameOf)
                .withEnchantColorOf(key -> {
                    String tier = library.tierOf(key);
                    if (tier == null) {
                        return null;
                    }
                    TierRegistry.Tier resolved = library.tiers().tier(tier);
                    return resolved != null && !resolved.color().isBlank() ? resolved.color() : null;
                })
                .withSetLore(new LoreRenderer.SetLore() {
                    @Override public List<String> armor(String setKey) {
                        SetDef def = library.setDefOf(setKey);
                        return def != null ? def.armorLore() : List.of();
                    }

                    @Override public List<String> armor(String setKey, String slotToken) {
                        SetDef def = library.setDefOf(setKey);
                        return def != null ? def.armorLoreFor(slotToken) : List.of();
                    }

                    @Override public List<String> weapon(String setKey) {
                        SetDef def = library.setDefOf(setKey);
                        return def != null ? def.weaponLore() : List.of();
                    }
                }), new PdcItemStateStore());
    }
}
