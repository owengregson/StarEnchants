/**
 * Transactional content reload (docs/architecture.md §10; ADR-0014). {@link platform.content.ContentReloader}
 * builds the next {@link compile.load.Library} off the main thread (the loader is pure) and swaps the
 * {@link compile.load.ContentHolder} by reference on the global thread only when the build is clean —
 * a fatal edit keeps the old content live, so a bad reload never takes the server down. Lives in
 * {@code se-platform} because it is the one part of the load path that needs {@code Scheduling}.
 */
package platform.content;
