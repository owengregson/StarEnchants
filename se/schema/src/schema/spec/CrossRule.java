package schema.spec;

import schema.diag.Diagnostics;
import schema.diag.Source;

/**
 * A validation spanning multiple {@link ParamSpec} arguments (e.g. "min must not exceed max").
 * Runs after every argument has parsed, so it may assume values are present and typed; reports
 * into {@link Diagnostics} and never throws (docs/architecture.md §7).
 */
@FunctionalInterface
public interface CrossRule {

    void check(Args args, Source source, Diagnostics diags);
}
