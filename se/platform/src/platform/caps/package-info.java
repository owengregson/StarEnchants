/**
 * The capability probe (docs/architecture.md §9): a boot-time, immutable
 * {@link platform.caps.Capabilities} snapshot of whether this is a Folia server and which
 * Minecraft version it is. Downstream code gates the {@code compat-folia}/{@code compat-modern}
 * edges on these flags rather than parsing version strings, so a renamed API or a new server
 * flavour is absorbed here, not branched on everywhere.
 */
package platform.caps;
