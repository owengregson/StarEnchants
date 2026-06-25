package compile.load;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import schema.diag.Diagnostics;
import schema.diag.Source;

/**
 * The ONLY loader class that touches SnakeYAML (§10): a position-preserving view carrying each value's
 * {@code file:line:col} {@link Source} so diagnostics point where the operator wrote it; a malformed
 * document is diagnosed into {@link Diagnostics}, never thrown. Uses only the {@code compose()}/{@code Node}/
 * {@code Mark} API — stable across SnakeYAML 1.x and 2.x — so it runs against the server-provided SnakeYAML
 * unshaded. (cross-version)
 */
final class YamlNode {

    private final String file;
    private final Node node;                  // null = an absent child or an empty document
    private final Map<String, Node> fields;   // populated when this node is a mapping

    private YamlNode(String file, Node node) {
        this.file = file;
        this.node = node;
        this.fields = new LinkedHashMap<>();
        if (node instanceof MappingNode mapping) {
            for (NodeTuple tuple : mapping.getValue()) {
                if (tuple.getKeyNode() instanceof ScalarNode key) {
                    fields.put(key.getValue(), tuple.getValueNode());
                }
            }
        }
    }

    /** Compose the root node of one document; a parse error is a diagnostic and yields an absent node. */
    static YamlNode compose(String file, String yaml, Diagnostics diags) {
        try {
            LoaderOptions options = new LoaderOptions();
            // SnakeYAML 2.x rejects duplicate keys; 1.x (older servers) allows them — force "last wins" so
            // a file parses identically on every server's SnakeYAML. (cross-version)
            options.setAllowDuplicateKeys(true);
            return new YamlNode(file, new Yaml(options).compose(new StringReader(yaml)));
        } catch (RuntimeException | StackOverflowError malformed) {
            // StackOverflowError too: a pathologically deep doc escapes as an Error; content must never
            // throw out of the loader, only diagnose (§7, §10).
            diags.error("load.yaml", "could not parse YAML: " + rootMessage(malformed), Source.ofFile(file));
            return new YamlNode(file, null);
        }
    }

    boolean isMapping() {
        return node instanceof MappingNode;
    }

    boolean has(String key) {
        return fields.containsKey(key);
    }

    /** This node's own position (or the whole-file source if it has no mark). */
    Source source() {
        return positionOf(node);
    }

    /** The position of the value under {@code key}, falling back to this node's position. */
    Source sourceOf(String key) {
        Node child = fields.get(key);
        return child != null ? positionOf(child) : source();
    }

    /** The scalar text under {@code key}, or {@code null} if absent / not a scalar. */
    String string(String key) {
        return fields.get(key) instanceof ScalarNode scalar ? scalar.getValue() : null;
    }

    /** Scalar items of a sequence under {@code key}; a lone scalar is treated as a one-element list. */
    List<String> stringList(String key) {
        List<String> out = new ArrayList<>();
        Node value = fields.get(key);
        if (value instanceof SequenceNode sequence) {
            for (Node item : sequence.getValue()) {
                if (item instanceof ScalarNode scalar) {
                    out.add(scalar.getValue());
                }
            }
        } else if (value instanceof ScalarNode scalar) {
            out.add(scalar.getValue());
        }
        return out;
    }

    /** The {@code (key, child)} entries of the mapping under {@code key} (e.g. the {@code levels:} map). */
    List<Entry> entries(String key) {
        List<Entry> out = new ArrayList<>();
        if (fields.get(key) instanceof MappingNode mapping) {
            for (NodeTuple tuple : mapping.getValue()) {
                if (tuple.getKeyNode() instanceof ScalarNode k) {
                    out.add(new Entry(k.getValue(), new YamlNode(file, tuple.getValueNode())));
                }
            }
        }
        return out;
    }

    /** This node's OWN {@code (key, child)} entries when it is a mapping (e.g. a verbose effect's body). */
    List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        if (node instanceof MappingNode mapping) {
            for (NodeTuple tuple : mapping.getValue()) {
                if (tuple.getKeyNode() instanceof ScalarNode k) {
                    out.add(new Entry(k.getValue(), new YamlNode(file, tuple.getValueNode())));
                }
            }
        }
        return out;
    }

    /**
     * The wrapped items of the sequence under {@code key} — wrapped so a caller can tell a scalar item
     * (terse {@code "HEAD:arg"} effect) from a mapping item (verbose {@code HEAD: {…}}). A lone
     * non-sequence value is returned as a one-element list, matching {@link #stringList}'s tolerance.
     */
    List<YamlNode> items(String key) {
        List<YamlNode> out = new ArrayList<>();
        Node value = fields.get(key);
        if (value instanceof SequenceNode sequence) {
            for (Node item : sequence.getValue()) {
                out.add(new YamlNode(file, item));
            }
        } else if (value != null) {
            out.add(new YamlNode(file, value));
        }
        return out;
    }

    /** The child node under {@code key} (an absent node if missing) — for reading a nested sub-mapping. */
    YamlNode child(String key) {
        return new YamlNode(file, fields.get(key));
    }

    /** Whether this node is a scalar leaf. */
    boolean isScalar() {
        return node instanceof ScalarNode;
    }

    /** This node's scalar text, or {@code null} if it is not a scalar. */
    String scalar() {
        return node instanceof ScalarNode scalar ? scalar.getValue() : null;
    }

    record Entry(String key, YamlNode value) {
    }

    private Source positionOf(Node n) {
        if (n == null) {
            return Source.ofFile(file);
        }
        Mark mark = n.getStartMark();
        return mark == null ? Source.ofFile(file) : Source.of(file, mark.getLine() + 1, mark.getColumn() + 1);
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
