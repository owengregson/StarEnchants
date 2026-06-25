/**
 * Cross-version handle resolution (docs/architecture.md §9): {@link platform.resolve.Aliases} holds the
 * rename knowledge, {@link platform.resolve.HandleResolver} the bidirectional order, and
 * {@link platform.resolve.RenameResolvers} the shared resolve-and-intern machinery.
 * {@link platform.resolve.VocabularyResolvers} (fixed vocabulary) serves the compiler in unit tests;
 * {@link platform.resolve.RegistryResolvers} (live registry probes) is the production resolver. The
 * runtime only ever sees the interned ids produced here, never a renamed constant.
 */
package platform.resolve;
