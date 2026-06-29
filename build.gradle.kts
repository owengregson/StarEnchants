// Root build — shared conventions for every module.
//
// Convention is applied reactively (`plugins.withId("java-library")`) so each
// module declares its own `plugins { java-library }` block (which gives it typed
// Kotlin DSL dependency accessors), while this root supplies the toolchain,
// repositories, and the JUnit 5 test stack once, in one place. The root project
// itself applies no plugins — it is a pure configuration container.

allprojects {
    group = "com.starenchants"
    version = "1.1.10-beta"
}

subprojects {
    // Isolate the two cross-version targets into SEPARATE build dirs so a modern build (`build/`) and a
    // legacy build (`-Pse.target=legacy` → `build-legacy/`) can never collide: no shared jar filename to
    // clobber, no shared classes dir for Gradle's incremental compiler to mix across the overlay srcDir swap,
    // and the mega-jar merge becomes order-independent (it reads the modern jar from build/ and the downgraded
    // legacy jar from build-legacy/). The legacy dir is gitignored. See scripts/build-mega-jar.sh.
    if ((findProperty("se.target") as String?) == "legacy") {
        layout.buildDirectory.set(layout.projectDirectory.dir("build-legacy"))
    }

    repositories {
        mavenCentral()
        maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
    }

    plugins.withId("java-library") {
        extensions.configure<JavaPluginExtension> {
            // Floor toolchain: every core module emits Java 17 class files so the
            // one universal jar loads on 1.17.1 → 26.1.x (docs/architecture.md §11).
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(17))
            }
            withSourcesJar()
        }

        // Flat source roots. Production code lives in `src/`, tests in `test/`,
        // resources alongside — not the Maven `src/main/java` / `src/test/java`,
        // whose build-convention segments carried no information, only depth.
        // Combined with single-segment packages (each module's package is just its
        // name — `schema`, `engine`, … — set in the files, not `com.starenchants.<m>`),
        // a file reads `se/schema/src/schema/diag/Severity.java`: the module, the
        // package, nothing else. (Shaded third-party deps must be relocated under
        // their own root so these short package roots never collide — §11.)
        extensions.configure<SourceSetContainer> {
            named("main") {
                java.setSrcDirs(listOf("src"))
                resources.setSrcDirs(listOf("resources"))
            }
            named("test") {
                java.setSrcDirs(listOf("test"))
                resources.setSrcDirs(listOf("test-resources"))
            }
        }

        dependencies {
            add("testImplementation", platform("org.junit:junit-bom:5.11.3"))
            add("testImplementation", "org.junit.jupiter:junit-jupiter")
            add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
            // ArchUnit — part of the shared test stack. Used today by the pure-module purity
            // guards (CorePurityArchTest); cheap and inert where a module declares no arch test.
            add("testImplementation", "com.tngtech.archunit:archunit-junit5:1.3.0")
        }

        // JaCoCo per module — the coverage LEDGER for the test-suite overhaul: a collapse/merge may not
        // drop a covered branch (docs/dev/internals/testing-architecture.md). Reports run on demand
        // (`./gradlew :<module>:jacocoTestReport`) so the inner loop stays fast; the CSV makes
        // BRANCH_COVERED counts trivially diffable before/after a rewrite.
        pluginManager.apply("jacoco")
        extensions.configure<JacocoPluginExtension> {
            toolVersion = "0.8.12"
        }
        tasks.withType<JacocoReport>().configureEach {
            reports {
                xml.required.set(true)
                csv.required.set(true)
                html.required.set(true)
            }
        }

        // ── Branch-coverage floor: the BLOCKING gate ──────────────────────────────────────────────────
        // A per-module minimum BRANCH ratio, wired into `check` so `./gradlew build` FAILS if a
        // collapse/merge silently drops covered branches — the structural backstop the overhaul's ledger
        // existed to enforce. Floors sit a few points under each module's current actual (schema 86.8 /
        // compile 63.5 / engine 63.1 / item 64.9 %): tight enough to catch a real regression, loose enough
        // that ordinary churn does not flap the gate. Only the pure-logic modules are listed — the
        // Bukkit-shell modules (platform/bootstrap/integrate/feature/compat-folia/pack/migrate/api) have
        // their branches exercised by the LIVE matrix, not unit tests, so a unit branch floor there would
        // be gamed or perpetually red (writing-tests skill, Rule 4 — live is reserved for what units can't reach).
        val branchFloor = mapOf(
            "schema" to "0.84".toBigDecimal(),
            "compile" to "0.60".toBigDecimal(),
            "engine" to "0.60".toBigDecimal(),
            "item" to "0.60".toBigDecimal(),
        )[project.name]
        if (branchFloor != null) {
            tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
                violationRules {
                    rule {
                        limit {
                            counter = "BRANCH"
                            value = "COVEREDRATIO"
                            minimum = branchFloor
                        }
                    }
                }
            }
            tasks.named("check") { dependsOn("jacocoTestCoverageVerification") }
        }

        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.compilerArgs.add("-Xlint:all,-processing")
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
            }
            // Forward opt-in `se.*` test knobs to the forked test JVM (e.g. the one-off catalog
            // migration-equivalence gate: -Dse.equiv.old=… -Dse.equiv.new=…). Inert when unset.
            System.getProperties().stringPropertyNames()
                .filter { it.startsWith("se.") }
                .forEach { systemProperty(it, System.getProperty(it)) }
        }
    }
}

// One command to rewrite every generated artifact (DSL reference, docs-site catalog + operator
// surface, content index) from the code/resources. The git pre-commit hook runs this when a source
// changes; the matching *DriftTest fails `./gradlew build` if a committed artifact is ever stale.
tasks.register("regenDocs") {
    group = "documentation"
    description = "Regenerate all generated docs/data artifacts from the sources."
    dependsOn(":engine:regenDocs", ":bootstrap:regenDocs")
}
