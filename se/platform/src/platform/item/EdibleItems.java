package platform.item;

import java.lang.reflect.Method;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import platform.caps.Capabilities;

/**
 * Force an arbitrary item to be EDIBLE — the eat-gesture seam behind the use-item {@code is-food} field (the
 * is-food design spec; ADR-0048). Modelled on the graceful-degrade precedent ({@code MenuIcons.glow},
 * {@link platform.caps.Capabilities}): {@link #makeEdible} sets the food/consumable data component so the client
 * plays the real eating animation and the server fires {@code PlayerItemConsumeEvent}, carrying nutrition 0,
 * saturation 0, can-always-eat true (a pure trigger, no hunger). Disabled below 1.20.5 (every method a no-op), so
 * an {@code is-food} item there simply behaves as an ordinary one-click use-item.
 *
 * <p>Data components live above the 1.17.1 compile floor, so ALL calls are reflective and resolved once here.
 * There are two version bands (verified against the reference cache, {@code nms-archaeology}):
 * <ul>
 *   <li><b>1.20.5&ndash;1.21.1</b> ({@link Mode#FOOD_META}): a {@code food} component with can-always-eat is what
 *       makes an item edible &mdash; set via {@code ItemMeta.setFood(FoodComponent)}.</li>
 *   <li><b>1.21.2+</b> incl. 26.1.x ({@link Mode#CONSUMABLE_DATA}): Mojang split {@code consumable} out of
 *       {@code food}; {@code food} alone no longer enables eating and {@code ItemMeta} has no consumable accessor,
 *       so edibility comes from Paper's {@code consumable} data component set on the ItemStack itself. The
 *       {@code consumable} API landed at 1.21.4, so 1.21.2/1.21.3 have NO working seam and {@link #decide}
 *       degrades them to {@link Mode#NONE} (one-click) rather than half-working.</li>
 * </ul>
 * The band is chosen by a capability probe (class presence), never a hardcoded version list, so the
 * 1.21.2/1.21.3 gap auto-degrades.
 */
public class EdibleItems {

    /** Inert seam (disabled): {@link #makeEdible} is a no-op. The value pure/unit/fake contexts hold. */
    public static final EdibleItems NONE = new EdibleItems();

    /** Which reflective band forces edibility on this server, or {@link #NONE} when there is no working seam. */
    enum Mode { NONE, FOOD_META, CONSUMABLE_DATA }

    private final Mode mode;
    // Band A (1.20.5–1.21.1): ItemMeta.getFood()/setFood(FoodComponent) + the FoodComponent setters.
    private final Method getFood;
    private final Method setFood;
    private final Method setNutrition;
    private final Method setSaturation;
    private final Method setCanAlwaysEat;
    // Band B (1.21.4+): ItemStack.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()).
    private final Object consumableType;
    private final Method consumableFactory;
    private final Method setData;

    /** Disabled seam for pure/unit contexts and recording fakes; {@link #enabled()} is false. */
    protected EdibleItems() {
        this(Mode.NONE, null, null, null, null, null, null, null, null);
    }

    private EdibleItems(Mode mode, Method getFood, Method setFood, Method setNutrition, Method setSaturation,
                        Method setCanAlwaysEat, Object consumableType, Method consumableFactory, Method setData) {
        this.mode = mode;
        this.getFood = getFood;
        this.setFood = setFood;
        this.setNutrition = setNutrition;
        this.setSaturation = setSaturation;
        this.setCanAlwaysEat = setCanAlwaysEat;
        this.consumableType = consumableType;
        this.consumableFactory = consumableFactory;
        this.setData = setData;
    }

    /**
     * Resolve the edibility seam for {@code caps}: the reflective handles for whichever band this server supports,
     * or {@link #NONE} when none does. A band whose types are absent (below its floor, or the 1.21.2/1.21.3 gap)
     * yields a disabled seam that degrades an {@code is-food} item to one-click.
     */
    public static EdibleItems of(Capabilities caps) {
        Objects.requireNonNull(caps, "caps");
        Method[] food = resolveFoodMeta();
        Object[] data = resolveConsumableData();
        return switch (decide(caps, food != null, data != null)) {
            case FOOD_META -> new EdibleItems(Mode.FOOD_META, food[0], food[1], food[2], food[3], food[4],
                    null, null, null);
            case CONSUMABLE_DATA -> new EdibleItems(Mode.CONSUMABLE_DATA, null, null, null, null, null,
                    data[0], (Method) data[1], (Method) data[2]);
            case NONE -> NONE;
        };
    }

    /**
     * The pure version-band decision (the one real cross-version risk, unit-tested): the eat gesture needs
     * 1.20.5+; on 1.21.2+ only the {@code consumable} component enables eating (so a 1.21.2/1.21.3 server with no
     * {@code consumable} API degrades to one-click); on 1.20.5&ndash;1.21.1 the {@code food} component does.
     */
    static Mode decide(Capabilities caps, boolean foodMetaPresent, boolean consumableDataPresent) {
        if (!caps.atLeast(1, 20, 5)) {
            return Mode.NONE;
        }
        if (caps.atLeast(1, 21, 2)) {
            return consumableDataPresent ? Mode.CONSUMABLE_DATA : Mode.NONE;
        }
        return foodMetaPresent ? Mode.FOOD_META : Mode.NONE;
    }

    /** Whether this server can force-eat an arbitrary item (1.20.5+ with a working seam). */
    public boolean enabled() {
        return mode != Mode.NONE;
    }

    /**
     * Make {@code stack} edible in place: nutrition 0, saturation 0, can-always-eat, default eat duration, so
     * eating it is a pure trigger gesture. A no-op when disabled or if the server/material refuses the component
     * (degrades to one-click, never throws) — the graceful-degrade contract of {@code MenuIcons.glow}.
     */
    public void makeEdible(ItemStack stack) {
        if (stack == null) {
            return;
        }
        switch (mode) {
            case FOOD_META -> makeEdibleViaFood(stack);
            case CONSUMABLE_DATA -> makeEdibleViaConsumable(stack);
            case NONE -> {
                // Disabled — leave the item non-edible (the runtime degrades an is-food def to one-click).
            }
        }
    }

    // Band A: getFood() returns a non-null default component even for a non-food material; can-always-eat is
    // REQUIRED so a full-hunger player can still eat at nutrition 0. Eat duration left at its 1.6s default.
    private void makeEdibleViaFood(ItemStack stack) {
        try {
            ItemMeta meta = stack.getItemMeta();
            if (meta == null) {
                return;
            }
            Object food = getFood.invoke(meta);
            setNutrition.invoke(food, 0);
            setSaturation.invoke(food, 0.0f);
            setCanAlwaysEat.invoke(food, true);
            setFood.invoke(meta, food);
            stack.setItemMeta(meta);
        } catch (ReflectiveOperationException | RuntimeException refuse) {
            // A server/material that refuses the food component → leave it non-edible, never broken.
        }
    }

    // Band B: the builder already defaults to the vanilla 1.6s eat duration + EAT animation; NO food component is
    // set, so the item is always-eatable at zero nutrition. setData mutates the stack directly (no meta round-trip).
    private void makeEdibleViaConsumable(ItemStack stack) {
        try {
            Object builder = consumableFactory.invoke(null);
            setData.invoke(stack, consumableType, builder);
        } catch (ReflectiveOperationException | RuntimeException refuse) {
            // ditto — refuse gracefully.
        }
    }

    /** Handles {@code {getFood, setFood, setNutrition, setSaturation, setCanAlwaysEat}}, or {@code null} if absent. */
    private static Method[] resolveFoodMeta() {
        try {
            Class<?> food = Class.forName("org.bukkit.inventory.meta.components.FoodComponent");
            return new Method[] {
                    ItemMeta.class.getMethod("getFood"),
                    ItemMeta.class.getMethod("setFood", food),
                    food.getMethod("setNutrition", int.class),
                    food.getMethod("setSaturation", float.class),
                    food.getMethod("setCanAlwaysEat", boolean.class),
            };
        } catch (ReflectiveOperationException absent) {
            return null;
        }
    }

    /** Handles {@code {CONSUMABLE type, Consumable.consumable(), ItemStack.setData}}, or {@code null} if absent. */
    private static Object[] resolveConsumableData() {
        try {
            Class<?> types = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
            Class<?> valued = Class.forName("io.papermc.paper.datacomponent.DataComponentType$Valued");
            Class<?> builder = Class.forName("io.papermc.paper.datacomponent.DataComponentBuilder");
            Class<?> consumable = Class.forName("io.papermc.paper.datacomponent.item.Consumable");
            return new Object[] {
                    types.getField("CONSUMABLE").get(null),
                    consumable.getMethod("consumable"),
                    ItemStack.class.getMethod("setData", valued, builder),
            };
        } catch (ReflectiveOperationException absent) {
            return null;
        }
    }
}
