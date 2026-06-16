plugins {
    `java-library`
}

dependencies {
    // Activated as implementation reaches this module:
    // compileOnly(libs.paper.api.floor)
    // api(project(":schema"))
    // api(project(":platform"))
}

// Legacy NBT reader (isolated reflection) + EE/EA/AE config importer. Reuses the
// se-schema ParamSpec and the se-platform alias maps; emits COMMENTED, reviewable
// StarEnchants YAML with inline TODOs (docs/architecture.md §2, §4.3, §10).
