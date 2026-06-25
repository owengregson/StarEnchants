/**
 * Transactional content reload (docs/architecture.md §10; ADR-0014). Lives in {@code se-platform}
 * because it is the one part of the load path that needs {@code Scheduling}.
 */
package platform.content;
