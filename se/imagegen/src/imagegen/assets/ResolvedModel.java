package imagegen.assets;

import java.util.List;

/**
 * How a material renders in an inventory slot, as resolved from the vanilla model files: a FLAT stack of texture
 * layers (the common case — most items, and block-items whose model is {@code item/generated}), a full-cube BLOCK
 * to draw isometrically, or UNKNOWN when assets are missing or the model isn't a shape this generator handles
 * (the GUI renderer then draws a labelled placeholder).
 */
public final class ResolvedModel {

    public enum Kind { FLAT, BLOCK, UNKNOWN }

    public final Kind kind;
    public final List<FlatLayer> layers; // FLAT only; else empty
    public final BlockModel block;       // BLOCK only; else null

    private ResolvedModel(Kind kind, List<FlatLayer> layers, BlockModel block) {
        this.kind = kind;
        this.layers = layers == null ? List.of() : List.copyOf(layers);
        this.block = block;
    }

    public static ResolvedModel flat(List<FlatLayer> layers) {
        return new ResolvedModel(Kind.FLAT, layers, null);
    }

    public static ResolvedModel block(BlockModel block) {
        return new ResolvedModel(Kind.BLOCK, List.of(), block);
    }

    public static ResolvedModel unknown() {
        return new ResolvedModel(Kind.UNKNOWN, List.of(), null);
    }
}
