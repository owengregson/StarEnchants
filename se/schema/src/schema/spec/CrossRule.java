package schema.spec;

import schema.diag.Diagnostics;
import schema.diag.Source;

/**
 * A validation that spans more than one argument of a {@link ParamSpec} — e.g.
 * "min must not exceed max" or "radius required when shape is CIRCLE".
 *
 * <p>Run after every individual argument has parsed successfully, so a rule can
 * assume the values it reads are present and typed. Rules report into the shared
 * {@link Diagnostics} and never throw (docs/architecture.md §7).
 */
@FunctionalInterface
public interface CrossRule {

    /**
     * @param args  the already-parsed, type-valid arguments
     * @param source the line's source position for any diagnostic
     * @param diags the collector to report violations into
     */
    void check(Args args, Source source, Diagnostics diags);
}
