package bootstrap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.load.ItemsConfig;
import compile.load.ItemsLoader;
import compile.load.ParticleSpec;
import compile.load.SoulGemConfig;
import compile.load.SoundCue;
import item.mint.ItemFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import platform.resolve.Aliases;
import platform.resolve.HandleResolver;
import platform.resolve.LegacyFallbacks;
import schema.spec.HandleCategory;

/**
 * Every cross-version HANDLE token an {@code items/} tree authors — material names, gesture cues and particle
 * tokens — harvested through the production {@link ItemsLoader} so the values are the loader's own rather than
 * re-typed here, and resolved by the same rule the runtime uses.
 *
 * <p>This is the half of a pack the compile gates cannot see: {@link LegacyHandleEraTest} and
 * {@link ModernHandleEraTest} run the CONTENT compiler, and nothing on the item path produces a diagnostic —
 * an unresolvable cue plays silence ({@code feature.compat.Sounds}), an unresolvable particle is skipped
 * ({@code feature.fx.ParticleFx}) and an unresolvable material mints as the caller's generic fallback. So a
 * spelling that only exists on one era ships as a mute gesture or the wrong icon with nothing to point at it.
 *
 * <p>Entity types are out of scope: the only item-side ENTITY_TYPE surface is the soul gem's optional
 * {@code souls-per-mob} map, which no shipped pack authors and for which no per-era fixture is committed.
 */
final class ItemHandles {

    /**
     * One authored token and the config field carrying it (the field only names a failure).
     *
     * @param minted whether {@code ItemFactory} builds a stack from this token, so its newer&rarr;older
     *               degradation applies — a {@code material-upgrades} KEY is instead compared against a live
     *               item's own type name, and only ever matches the era's own spelling
     */
    record Token(String field, String value, boolean minted) {
    }

    private final List<Token> materials = new ArrayList<>();
    private final List<Token> sounds = new ArrayList<>();
    private final List<Token> particles = new ArrayList<>();

    private ItemHandles() {
    }

    /** Tokens that name no MATERIAL an era holding {@code constants} has, as {@code field=TOKEN}; empty = clean. */
    String unresolvableMaterials(Set<String> constants) {
        return report(materials, token -> {
            String name = canonical(token.value());
            if (constants.contains(name)) {
                return true;
            }
            // ItemFactory's mint-path degradation. Inert on a modern era — every key of that table is itself a
            // modern constant, so the branch above already answered — and never applied to a matched token.
            String older = token.minted() ? ItemFactory.legacyFallback(name) : null;
            return older != null && constants.contains(older);
        });
    }

    /** Tokens that name no SOUND an era holding {@code constants} has, as {@code field=TOKEN}; empty = clean. */
    String unresolvableSounds(Set<String> constants) {
        // The table feature.compat.Sounds resolves item cues against, on both lanes: renames + degradations.
        Map<String, String> table = Aliases.mergedWith(
                HandleCategory.SOUND, LegacyFallbacks.forCategory(HandleCategory.SOUND));
        return report(sounds, token ->
                HandleResolver.resolve(SoundCue.canonical(token.value()), table, constants::contains).isPresent());
    }

    /** Tokens that name no PARTICLE an era holding {@code constants} has, as {@code field=TOKEN}; empty = clean. */
    String unresolvableParticles(Set<String> constants) {
        return report(particles, token -> HandleResolver.resolve(
                token.value(), Aliases.forCategory(HandleCategory.PARTICLE), constants::contains).isPresent());
    }

    private static String report(List<Token> tokens, Predicate<Token> resolves) {
        return tokens.stream()
                .filter(token -> !resolves.test(token))
                .map(token -> token.field() + "=" + token.value())
                .sorted()
                .distinct()
                .collect(Collectors.joining(", "));
    }

    /** {@code Material.matchMaterial}'s normalisation for the spellings a config may carry. */
    private static String canonical(String token) {
        String name = token.trim();
        int colon = name.indexOf(':');
        return name.substring(colon + 1).toUpperCase(Locale.ROOT);
    }

    static ItemHandles of(Path itemsRoot) {
        assertTrue(Files.isDirectory(itemsRoot),
                () -> itemsRoot + " not found from " + Path.of("").toAbsolutePath());
        ItemsConfig items = ItemsLoader.load(itemsRoot);
        ItemHandles out = new ItemHandles();

        items.soulGem().ifPresent(gem -> {
            out.minted("soul-gem.material", gem.material());
            SoulGemConfig.Sounds cues = gem.sounds();
            out.cues("soul-gem.sounds.toggle-on", cues.toggleOn());
            out.cues("soul-gem.sounds.toggle-off", cues.toggleOff());
            out.cues("soul-gem.sounds.use", cues.use());
            out.cues("soul-gem.sounds.combine", cues.combine());
            out.cues("soul-gem.sounds.split", cues.split());
            SoulGemConfig.Particles bursts = gem.particles();
            out.spec("soul-gem.particles.enable", bursts.enable());
            out.spec("soul-gem.particles.disable", bursts.disable());
            out.spec("soul-gem.particles.idle", bursts.idle());
            out.spec("soul-gem.particles.use", bursts.use());
        });
        items.crystal().ifPresent(crystal -> {
            out.minted("crystal.material", crystal.material());
            out.minted("crystal.extractor.material", crystal.extractorMaterial());
            out.cue("crystal.sounds.apply", crystal.soundApply());
            out.cue("crystal.sounds.remove", crystal.soundRemove());
        });
        items.heroic().ifPresent(heroic -> {
            out.minted("heroic.material", heroic.material());
            heroic.materialUpgrades().forEach((from, to) -> {
                out.matched("heroic.material-upgrades key", from);
                out.minted("heroic.material-upgrades[" + from + "]", to);
            });
        });
        items.slots().ifPresent(slots -> out.minted("slot-orb.orb-material", slots.orbMaterial()));
        items.scrolls().ifPresent(scrolls -> {
            out.minted("black-scroll.material", scrolls.black().material());
            out.minted("randomizer-scroll.material", scrolls.randomizer().material());
            out.minted("transmog-scroll.material", scrolls.transmog().material());
            out.minted("holy-white-scroll.material", scrolls.holy().material());
            out.minted("nametag.material", scrolls.nametag().material());
            out.minted("godly-transmog.material", scrolls.godly().material());
        });
        items.unopenedBook().ifPresent(book -> out.minted("unopened-book.material", book.material()));
        items.enchantBook().ifPresent(book -> out.minted("enchant-book.material", book.material()));
        items.whiteScroll().ifPresent(scroll -> out.minted("white-scroll.material", scroll.material()));
        items.dust().ifPresent(dust -> {
            out.minted("dust.material", dust.material());
            out.cue("dust.sound", dust.sound());
            out.bursts("dust.particles", dust.particles());
        });
        items.traks().ifPresent(traks -> {
            out.minted("blocktrak.material", traks.block().material());
            out.minted("mobtrak.material", traks.mob().material());
            out.minted("soultrak.material", traks.soul().material());
            out.minted("fishtrak.material", traks.fish().material());
        });
        items.petFood().ifPresent(food -> {
            out.minted("pet-food.material", food.material());
            out.cue("pet-food.sound", food.sound());
            out.bursts("pet-food.particles", food.particles());
        });
        items.mask().ifPresent(mask -> {
            out.cue("mask.sounds.apply", mask.soundApply());
            out.cue("mask.sounds.remove", mask.soundRemove());
        });
        items.reforge().ifPresent(reforge -> {
            out.cue("reforge.sounds.apply", reforge.soundApply());
            out.cue("reforge.sounds.remove", reforge.soundRemove());
        });
        // items/pet.yml carries no handle of its own — a pet's head material is per-pet, in content/pets/.

        // A tree that harvested nothing would make every gate below it vacuously green. Materials and cues are
        // structural (a pack that ships items/ ships a gem and a crystal); particles are genuinely optional.
        assertTrue(!out.materials.isEmpty() && !out.sounds.isEmpty(),
                () -> itemsRoot + " harvested no material or sound handles at all");
        return out;
    }

    private void minted(String field, String token) {
        add(materials, field, token, true);
    }

    private void matched(String field, String token) {
        add(materials, field, token, false);
    }

    private void cue(String field, SoundCue cue) {
        if (cue != null) {
            add(sounds, field, cue.name(), false);
        }
    }

    private void cues(String field, List<SoundCue> layers) {
        layers.forEach(layer -> cue(field, layer));
    }

    private void spec(String field, ParticleSpec spec) {
        if (!spec.isEmpty()) {
            add(particles, field, spec.type(), false);
        }
    }

    private void bursts(String field, List<String> tokens) {
        tokens.forEach(token -> add(particles, field, token, false));
    }

    /** A blank token is the authored way to say "none", so it is nothing to resolve rather than a fault. */
    private static void add(List<Token> into, String field, String token, boolean minted) {
        if (token != null && !token.isBlank()) {
            into.add(new Token(field, token, minted));
        }
    }
}
