plugins {
    `java-library`
}

dependencies {
    // Diagnostics + the unified vocabulary the importer targets; the importer needs schema's
    // Diagnostic types directly.
    api(project(":schema"))

    // Legacy configs are parsed with SnakeYAML directly (the same reader se-compile's YamlNode uses),
    // via the version-stable Yaml.load → Map API — NOT Bukkit's YamlConfiguration, whose internal
    // SnakeYAML calls are version-coupled. compileOnly: the server bundles SnakeYAML (docs §9).
    compileOnly("org.yaml:snakeyaml:2.2")

    testImplementation("org.yaml:snakeyaml:2.2")
    // The importer's headline test compiles its OWN output through the real production compiler +
    // builtin effect registry, proving migrated YAML is valid StarEnchants content (not just well-formed).
    testImplementation(project(":engine"))
    testImplementation(libs.paper.api.floor) // the engine's effect kinds reference Bukkit types
    testImplementation(project(":testfx")) // CorePurityArchTest calls testfx.Purity
}

// EE/EA/AE config importer (docs/architecture.md §2, §10): parse a legacy plugin's configs into a
// small intermediate model, map triggers/applies/effect-heads/targets to the unified vocabulary, and
// emit COMMENTED, reviewable StarEnchants YAML — faithfully translating what it can and flagging the
// rest with inline `# TODO` markers + diagnostics, never guessing silently. Pure logic; no runtime
// scheduling or item surface (legacy NBT migration lives in se-item, §4.3).
