/**
 * The compiler pipeline stages and their intermediate types
 * (docs/architecture.md §2 "se-compile", §3.2, §4.1). The spine is a sequence of
 * pure functions, each from one fixed type to the next:
 *
 * <pre>
 *   AbilityDef  --LowerStage-->  LoweredAbility  --EraseStage-->  ErasedContent  --SnapshotStage-->  Snapshot
 * </pre>
 *
 * Because every stage's input and output type is fixed (in {@link compile.model}, {@link compile.def},
 * and here), the stage implementations are independent — each unit-testable in isolation. A
 * {@code resolve} stage (cross-version handle resolution via the injected {@code PlatformResolvers})
 * runs between lowering and erasure.
 */
package compile.stage;
