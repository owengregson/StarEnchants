/**
 * The boot-time capability probe (docs/architecture.md §9): an immutable
 * {@link platform.caps.Capabilities} snapshot that downstream edges gate on instead of parsing
 * version strings, so a renamed API or new server flavour is absorbed here, not branched on everywhere.
 */
package platform.caps;
