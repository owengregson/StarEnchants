package imagegen.assets;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.yaml.snakeyaml.Yaml;

/**
 * Walks the vanilla item/block model JSON for one material and produces a {@link ResolvedModel}. Kept separate from
 * {@link VanillaAssets} so the asset-location/IO concerns stay in the factory and the model-format knowledge lives
 * here. Reads bytes through {@link VanillaAssets.AssetReader} (dir or zip — it doesn't care which).
 *
 * <p>Resolution mirrors how Minecraft loads inventory icons: read {@code models/item/<id>.json}, follow the
 * {@code parent} chain merging {@code textures} child-over-parent, then classify by the deepest parent reached:
 * the {@code item/generated}|{@code item/handheld} flat family, or one of the handled cube parents. Anything else
 * (multipart, builtin/entity, an unhandled cube variant) degrades to {@link ResolvedModel#unknown()} — never throws.
 */
final class ModelResolver {

    /** Bound the parent walk so a malformed/cyclic chain can never spin forever. */
    private static final int MAX_PARENT_DEPTH = 32;

    private final VanillaAssets.AssetReader reader;
    // Caches keyed by resource id so repeated slots of the same material cost nothing.
    private final Map<String, ResolvedModel> modelCache = new HashMap<>();
    private final Map<String, BufferedImage> textureCache = new HashMap<>();

    ModelResolver(VanillaAssets.AssetReader reader) {
        this.reader = reader;
    }

    ResolvedModel resolve(String materialName) {
        String id = normalise(materialName);
        if (id.isEmpty()) {
            return ResolvedModel.unknown();
        }
        ResolvedModel cached = modelCache.get(id);
        if (cached != null) {
            return cached;
        }
        ResolvedModel resolved;
        try {
            resolved = resolveUncached(id);
        } catch (RuntimeException e) {
            // Defensive: any unforeseen shape becomes a placeholder, the generator keeps going.
            warn("model '" + id + "' failed to resolve (" + e.getClass().getSimpleName() + ")");
            resolved = ResolvedModel.unknown();
        }
        modelCache.put(id, resolved);
        return resolved;
    }

    /** {@code DIAMOND_SWORD} / {@code minecraft:diamond_sword} → {@code diamond_sword}. */
    private static String normalise(String materialName) {
        if (materialName == null) {
            return "";
        }
        String s = materialName.trim().toLowerCase(java.util.Locale.ROOT);
        int colon = s.indexOf(':');
        if (colon >= 0) {
            s = s.substring(colon + 1);
        }
        return s;
    }

    private ResolvedModel resolveUncached(String id) {
        // Merged across the chain: textures (child wins), plus the deepest parent ref seen and the leaf's own keys.
        Map<String, Object> mergedTextures = new LinkedHashMap<>();
        String parentRef = null;
        Map<String, Object> leaf = readModel("item/" + id);
        if (leaf == null) {
            warn("missing model item/" + id + ".json");
            return ResolvedModel.unknown();
        }

        Map<String, Object> current = leaf;
        String currentName = "item/" + id;
        int depth = 0;
        while (current != null && depth++ < MAX_PARENT_DEPTH) {
            mergeTextures(mergedTextures, current);
            Object p = current.get("parent");
            String parent = p instanceof String ? stripNs((String) p) : null;

            if (parent == null) {
                // No parent: the leaf itself defines the shape — treat layerN as flat (block-state shapes have none).
                return classifyLeaf(currentName, mergedTextures);
            }
            parentRef = parent;
            if (isFlatParent(parent)) {
                return flatFrom(mergedTextures);
            }
            if (isCubeParent(parent)) {
                return blockFrom(parent, mergedTextures);
            }
            if (isBuiltin(parent)) {
                // builtin/entity (chests, shulker boxes, banners…) — not a static texture shape.
                return ResolvedModel.unknown();
            }
            // Keep walking: this parent is itself a custom model that refines a deeper one.
            Map<String, Object> next = readModel(parent);
            if (next == null) {
                // Parent unreadable but named a known family by convention? classify on the name as a last resort.
                if (parent.startsWith("block/")) {
                    return blockFrom(parent.substring("block/".length()), mergedTextures);
                }
                warn("missing parent model " + parent + ".json (chain of item/" + id + ")");
                return classifyLeaf(currentName, mergedTextures);
            }
            current = next;
            currentName = parent;
        }
        // Ran out the chain without hitting a known family.
        return classifyLeaf(parentRef != null ? parentRef : currentName, mergedTextures);
    }

    /** Last-resort classification when the chain ends at a model with no recognised parent. */
    private ResolvedModel classifyLeaf(String name, Map<String, Object> textures) {
        if (textures.containsKey("layer0")) {
            return flatFrom(textures);
        }
        if (name.startsWith("block/")) {
            return blockFrom(name.substring("block/".length()), textures);
        }
        return ResolvedModel.unknown();
    }

    // ---- flat (item/generated, item/handheld) ----

    private ResolvedModel flatFrom(Map<String, Object> textures) {
        List<FlatLayer> layers = new ArrayList<>();
        for (int i = 0; ; i++) {
            Object v = textures.get("layer" + i);
            if (!(v instanceof String)) {
                break;
            }
            BufferedImage img = loadTextureRef((String) v, textures);
            if (img != null) {
                // Tinting is out of scope (see FlatLayer javadoc): always -1.
                layers.add(new FlatLayer(img, -1));
            }
        }
        return layers.isEmpty() ? ResolvedModel.unknown() : ResolvedModel.flat(layers);
    }

    // ---- block (handled cube parents) ----

    private ResolvedModel blockFrom(String cubeParent, Map<String, Object> textures) {
        BufferedImage up;
        BufferedImage down;
        BufferedImage north;
        BufferedImage south;
        BufferedImage east;
        BufferedImage west;

        switch (cubeParent) {
            case "cube_all" -> {
                BufferedImage all = face("all", textures);
                up = down = north = south = east = west = all;
            }
            case "cube_column", "cube_column_horizontal" -> {
                BufferedImage end = face("end", textures);
                BufferedImage side = face("side", textures);
                up = down = end;
                north = south = east = west = side;
            }
            case "cube_bottom_top" -> {
                up = face("top", textures);
                down = face("bottom", textures);
                BufferedImage side = face("side", textures);
                north = south = east = west = side;
            }
            case "cube" -> {
                up = face("up", textures);
                down = face("down", textures);
                north = face("north", textures);
                south = face("south", textures);
                east = face("east", textures);
                west = face("west", textures);
            }
            case "orientable", "orientable_with_bottom", "orientable_vertical" -> {
                up = face("top", textures);
                down = face("bottom", textures);
                BufferedImage side = face("side", textures);
                north = face("front", textures); // front faces north (the gui transform's visible front)
                south = east = west = side;
            }
            default -> {
                up = down = north = south = east = west = null;
            }
        }

        // Fall back to whatever single texture is present for any unresolved face (e.g. only #all, or only #texture).
        BufferedImage fill = firstNonNull(up, side(north, south, east, west), anyTexture(textures));
        up = orElse(up, fill);
        down = orElse(down, fill);
        north = orElse(north, fill);
        south = orElse(south, fill);
        east = orElse(east, fill);
        west = orElse(west, fill);

        if (up == null && north == null && east == null) {
            return ResolvedModel.unknown();
        }
        return ResolvedModel.block(new BlockModel(up, down, north, south, east, west));
    }

    private BufferedImage face(String var, Map<String, Object> textures) {
        Object v = textures.get(var);
        return v instanceof String ? loadTextureRef((String) v, textures) : null;
    }

    // ---- texture refs ----

    /**
     * Resolve a model texture value to a PNG: either a {@code #ref} (resolve transitively against {@code textures})
     * or a resource path ({@code minecraft:block/stone} → {@code textures/block/stone.png}).
     */
    private BufferedImage loadTextureRef(String value, Map<String, Object> textures) {
        String v = value;
        int guard = 0;
        while (v != null && v.startsWith("#") && guard++ < MAX_PARENT_DEPTH) {
            Object next = textures.get(v.substring(1));
            v = next instanceof String ? (String) next : null;
        }
        if (v == null || v.startsWith("#")) {
            return null;
        }
        return loadTexture("textures/" + stripNs(v) + ".png");
    }

    /** Any concrete (non-#ref) texture value in the map — the uniform-cube fallback. */
    private BufferedImage anyTexture(Map<String, Object> textures) {
        for (Object v : textures.values()) {
            if (v instanceof String s && !s.startsWith("#")) {
                BufferedImage img = loadTexture("textures/" + stripNs(s) + ".png");
                if (img != null) {
                    return img;
                }
            }
        }
        return null;
    }

    BufferedImage loadTexture(String pathUnderAssets) {
        if (textureCache.containsKey(pathUnderAssets)) {
            return textureCache.get(pathUnderAssets); // cached, incl. a cached miss (null)
        }
        BufferedImage img = null;
        byte[] bytes = reader.read(pathUnderAssets);
        if (bytes != null) {
            try {
                img = ImageIO.read(new ByteArrayInputStream(bytes));
            } catch (Exception e) {
                warn("texture decode failed: " + pathUnderAssets);
            }
        }
        if (img == null) {
            warn("missing texture " + pathUnderAssets);
        }
        textureCache.put(pathUnderAssets, img); // cache the miss too (null) so we warn once
        return img;
    }

    // ---- model JSON ----

    @SuppressWarnings("unchecked")
    private Map<String, Object> readModel(String name) {
        byte[] bytes = reader.read("models/" + name + ".json");
        if (bytes == null) {
            return null;
        }
        try (Reader r = new StringReader(new String(bytes, StandardCharsets.UTF_8))) {
            Object loaded = new Yaml().load(r); // JSON is a YAML subset
            return loaded instanceof Map ? (Map<String, Object>) loaded : null;
        } catch (Exception e) {
            warn("model parse failed: models/" + name + ".json");
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void mergeTextures(Map<String, Object> into, Map<String, Object> model) {
        Object t = model.get("textures");
        if (t instanceof Map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) t).entrySet()) {
                // Child-over-parent: only fill keys the child (already merged) didn't define.
                into.putIfAbsent(e.getKey(), e.getValue());
            }
        }
    }

    // ---- parent classification ----

    private static boolean isFlatParent(String parent) {
        return parent.equals("item/generated") || parent.equals("item/handheld")
                || parent.equals("builtin/generated");
    }

    private static boolean isCubeParent(String parent) {
        return switch (stripBlock(parent)) {
            case "cube_all", "cube", "cube_column", "cube_column_horizontal", "cube_bottom_top",
                 "orientable", "orientable_with_bottom", "orientable_vertical" -> parent.startsWith("block/");
            default -> false;
        };
    }

    private static boolean isBuiltin(String parent) {
        return parent.startsWith("builtin/") && !parent.equals("builtin/generated");
    }

    private static String stripBlock(String parent) {
        return parent.startsWith("block/") ? parent.substring("block/".length()) : parent;
    }

    private static String stripNs(String s) {
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1) : s;
    }

    // blockFrom helpers
    private static BufferedImage side(BufferedImage... candidates) {
        return firstNonNull(candidates);
    }

    private static BufferedImage firstNonNull(BufferedImage... imgs) {
        for (BufferedImage i : imgs) {
            if (i != null) {
                return i;
            }
        }
        return null;
    }

    private static BufferedImage orElse(BufferedImage img, BufferedImage fallback) {
        return img != null ? img : fallback;
    }

    private static void warn(String msg) {
        System.err.println("[imagegen] " + msg);
    }
}
