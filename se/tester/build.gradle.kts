plugins {
    `java-library`
}

dependencies {
    // Activated as implementation reaches the harness:
    // compileOnly(libs.paper.api.floor)
    // implementation(project(":engine"))
    // implementation(project(":feature"))
}

// Live Paper + Folia in-server matrix harness. Boots a real server per
// (platform, version), installs StarEnchants + this jar, runs in-server suites,
// writes fresh PASS/FAIL, shuts down. A green Paper run says nothing about Folia
// (docs/architecture.md §2, §11; see the live-server-testing + matrix-gate skills).
