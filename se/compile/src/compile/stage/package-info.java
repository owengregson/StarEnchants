/**
 * The compiler pipeline stages and their intermediate types (docs/architecture.md §4.1). The spine is a
 * sequence of pure functions, each from one fixed type to the next:
 *
 * <pre>
 *   AbilityDef  --LowerStage-->  LoweredAbility  --EraseStage-->  ErasedContent  --SnapshotStage-->  Snapshot
 * </pre>
 *
 * A {@code resolve} stage (cross-version handle resolution via the injected {@code PlatformResolvers})
 * runs between lowering and erasure.
 */
package compile.stage;
