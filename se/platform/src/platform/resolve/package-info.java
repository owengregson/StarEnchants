/**
 * Cross-version handle resolution (docs/architecture.md §9). {@link platform.resolve.Aliases} is the
 * legacy&rarr;modern rename knowledge and {@link platform.resolve.HandleResolver} the bidirectional
 * resolution order; {@link platform.resolve.RenameResolvers} is the shared resolve-and-intern
 * machinery both resolvers ride on. {@link platform.resolve.VocabularyResolvers} (pure, fixed
 * vocabulary) lets the compiler resolve handles in unit tests; {@link platform.resolve.RegistryResolvers}
 * (backed by {@link platform.resolve.RegistrySupport}'s live registry/{@code valueOf} probes) is the
 * production resolver that confirms a token actually exists on this Paper/Folia version. The runtime
 * only ever sees the interned ids produced here, never a renamed constant.
 */
package platform.resolve;
