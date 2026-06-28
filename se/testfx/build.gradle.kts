plugins {
    `java-library`
}

// TEST-SUPPORT ONLY — never shipped in the plugin jar (like :tester and :imagegen). Holds the shared
// unit-test fixtures (FakeEffectCtx/SpecDrivenCtx, Defs, YamlFixture, RenderGolden, TtlStoreAdapter,
// CorpusLoader). The flat single-segment layout otherwise forbids cross-module test-source sharing, so
// without this module every fixture is re-duplicated per module — exactly the copy-paste the test-suite
// overhaul removes (docs/testing-overhaul/testing-architecture.md, open decision #2).
//
// A consuming module opts in with `testImplementation(project(":testfx"))`. Pure modules (schema, compile)
// only do so when a test actually needs a fixture; the wider transitive test classpath is harmless because
// the MAIN-source purity boundary is enforced separately by CorePurityArchTest.

dependencies {
    // The production types the fixtures build against: EffectCtx/EffectSpec/Sink/the stores (engine, which
    // brings compile→schema and platform transitively), the codec + LoreRenderer (item). api so a consumer
    // gets them on its test classpath without re-declaring.
    api(project(":engine"))
    api(project(":item"))

    // Fixtures reference Bukkit SPI types (EffectCtx returns LivingEntity/Location, RenderGolden walks an
    // ItemStack). compileOnly — the floor API is server-provided, and every consuming module already adds
    // paper-api to its own test classpath (matches the convention used across the modules).
    compileOnly(libs.paper.api.floor)

    // YamlFixture round-trips authored YAML through the real compiler; the server bundles SnakeYAML, so it
    // is compileOnly here and supplied by the consuming test classpath, never shaded.
    compileOnly("org.yaml:snakeyaml:2.2")
}
