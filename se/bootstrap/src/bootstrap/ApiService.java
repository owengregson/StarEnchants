package bootstrap;

import api.StarEnchantsApi;
import api.spi.AddonEffect;
import compile.load.ContentHolder;
import compile.load.EnchantDef;
import engine.effect.EffectKind;
import engine.effect.EffectRegistry;
import engine.interact.SlotLedger;
import item.codec.CombatState;
import item.view.ItemView;
import item.view.ItemViewCache;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.bukkit.inventory.ItemStack;
import platform.content.ContentReloader;

/**
 * The {@link StarEnchantsApi} implementation over the live services (ADR-0038), registered with Bukkit's
 * {@code ServicesManager} in {@code onEnable}. Add-on effect kinds are adapted to the engine by
 * {@link AddonBridge} and held in a concurrent list the composition root folds into the effect registry on
 * every build (initial + each reload), so an add-on head is both compilable and runnable and survives
 * {@code /se reload}. Item queries decode through the same {@link ItemViewCache} the combat path uses; enchant
 * keys and reloads read/drive the live {@link ContentHolder} / {@link ContentReloader}.
 */
final class ApiService implements StarEnchantsApi {

    private final CopyOnWriteArrayList<EffectKind> addonKinds;
    private final Supplier<EffectRegistry> effectRegistry;
    private final ContentReloader reloader;
    private final ContentHolder content;
    private final ItemViewCache itemViews;
    private final IntSupplier baseSlots;

    ApiService(CopyOnWriteArrayList<EffectKind> addonKinds, Supplier<EffectRegistry> effectRegistry,
               ContentReloader reloader, ContentHolder content, ItemViewCache itemViews, IntSupplier baseSlots) {
        this.addonKinds = Objects.requireNonNull(addonKinds, "addonKinds");
        this.effectRegistry = Objects.requireNonNull(effectRegistry, "effectRegistry");
        this.reloader = Objects.requireNonNull(reloader, "reloader");
        this.content = Objects.requireNonNull(content, "content");
        this.itemViews = Objects.requireNonNull(itemViews, "itemViews");
        this.baseSlots = Objects.requireNonNull(baseSlots, "baseSlots");
    }

    @Override
    public CompletableFuture<Boolean> registerEffect(AddonEffect effect) {
        Objects.requireNonNull(effect, "effect");
        String head = effect.spec().head().toUpperCase(Locale.ROOT);
        // Reject a duplicate head up front so the shared list can never be poisoned with a kind that would
        // fail every subsequent registry build (the underlying EffectRegistry.builder also throws on dupes).
        if (effectRegistry.get().heads().contains(head)) {
            throw new IllegalArgumentException("effect head already registered: " + head);
        }
        addonKinds.add(new AddonBridge(effect));
        return reloadContent();
    }

    @Override
    public CompletableFuture<Boolean> reloadContent() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        reloader.reload(result -> future.complete(result.published()));
        return future;
    }

    @Override
    public Map<String, Integer> enchantsOf(ItemStack stack) {
        return view(stack).enchants();
    }

    @Override
    public List<String> crystalsOf(ItemStack stack) {
        return view(stack).crystals();
    }

    @Override
    public Optional<String> setOf(ItemStack stack) {
        return Optional.ofNullable(view(stack).setKey());
    }

    @Override
    public OptionalInt slotsOf(ItemStack stack) {
        ItemView v = itemViews.of(stack);
        if (v.isEmpty()) {
            return OptionalInt.empty();
        }
        CombatState combat = v.combat();
        SlotLedger ledger = new SlotLedger(Math.max(0, baseSlots.getAsInt()), combat.added(), combat.enchants().size());
        return OptionalInt.of(ledger.remaining());
    }

    @Override
    public Set<String> enchantKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (EnchantDef def : content.library().catalog()) {
            keys.add(def.key());
        }
        return keys;
    }

    private CombatState view(ItemStack stack) {
        return itemViews.of(stack).combat();
    }
}
