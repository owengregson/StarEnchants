plugins {
    `java-library`
}

dependencies {
    // Activated as implementation reaches this module:
    // compileOnly(libs.paper.api.floor)
    // api(project(":engine"))
    // api(project(":item"))
    // api(project(":platform"))
    // api(project(":api"))
}

// Thin Bukkit FEATURE shells — one package each, "copy a sibling to add one":
// enchants/ armor/ crystals/ heroic/ souls/ scrolls/ dust/ slots/ nametag/
// crates/ crafting/ menus/ trading/ table/. No business logic that isn't a call
// into engine/item/economy (docs/architecture.md §2, §2.1).
