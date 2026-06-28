plugins {
    `java-library`
}

// TEST-SUPPORT ONLY — never shipped in the plugin jar (like :tester and :imagegen). Holds the shared
// unit-test fixtures (FakeEffectCtx/SpecDrivenCtx, Defs, YamlFixture, RenderGolden, TtlStoreAdapter,
// CorpusLoader). The flat single-segment layout otherwise forbids cross-module test-source sharing, so
// without this module every fixture is re-duplicated per module — exactly the copy-paste the test-suite
// overhaul removes (docs/dev/internals/testing-architecture.md, open decision #2).
//
// A consuming module opts in with `testImplementation(project(":testfx"))`. Pure modules (schema, compile)
// only do so when a test actually needs a fixture; the wider transitive test classpath is harmless because
// the MAIN-source purity boundary is enforced separately by CorePurityArchTest.

dependencies {
    // The production modules the fixtures build against are ALL compileOnly — never transitive. This is
    // load-bearing: a consumer adds testImplementation(project(":testfx")), and if any of these were `api`,
    // that consumer ↔ testfx would be a circular project dependency (e.g. compile uses Defs while testfx
    // builds Defs against compile). compileOnly keeps the task graph acyclic, and each CONSUMER supplies the
    // production module its fixtures touch (compile tests have compile for Defs/YamlFixture; engine tests
    // have engine for FakeEffectCtx; item tests have item for RenderGolden) — so it also never drags a heavy
    // Bukkit module onto a pure module's test classpath.
    compileOnly(project(":compile"))
    compileOnly(project(":engine"))
    compileOnly(project(":item"))

    // Fixtures reference Bukkit SPI types (EffectCtx returns LivingEntity/Location, RenderGolden walks an
    // ItemStack). compileOnly — the floor API is server-provided, and every consuming module already adds
    // paper-api to its own test classpath (matches the convention used across the modules).
    compileOnly(libs.paper.api.floor)

    // YamlFixture round-trips authored YAML through the real compiler; the server bundles SnakeYAML, so it
    // is compileOnly here and supplied by the consuming test classpath, never shaded.
    compileOnly("org.yaml:snakeyaml:2.2")
}
