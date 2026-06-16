// Root build — shared conventions for every module.
//
// Convention is applied reactively (`plugins.withId("java-library")`) so each
// module declares its own `plugins { java-library }` block (which gives it typed
// Kotlin DSL dependency accessors), while this root supplies the toolchain,
// repositories, and the JUnit 5 test stack once, in one place. The root project
// itself applies no plugins — it is a pure configuration container.

allprojects {
    group = "com.starenchants"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
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

        // Flat source roots. The Maven default buries every file under
        // `src/main/java/com/starenchants/<module>/…` — five fixed segments before
        // a single line of module code. The module already lives at `se/<module>/`
        // and the package is `com.starenchants.<module>`, so `src/main/java` adds no
        // information, only depth. We collapse it: production code lives in `src/`,
        // tests in `test/`, resources alongside. A file then reads
        // `se/schema/src/com/starenchants/schema/…` — the package, and nothing else.
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
        }
    }
}
