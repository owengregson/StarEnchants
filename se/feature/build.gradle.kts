plugins {
    `java-library`
}

dependencies {
    // Floor API: feature shells touch Bukkit events/entities; the server provides it.
    compileOnly(libs.paper.api.floor)

    // The runtime + item layers a feature shell calls into. engine brings :compile/:platform/:schema
    // transitively; item brings the codec/ItemView/WornState. A feature shell is thin glue.
    api(project(":engine"))
    api(project(":item"))

    testImplementation(libs.paper.api.floor)
    testImplementation(libs.mockito.core)
}

// Thin Bukkit FEATURE shells. The combat shell (feature.combat) bridges Bukkit damage/equip events
// to the engine: EquipListener resolves a player's WornState on equip change into the WornStateStore,
// and CombatListener dispatches EntityDamageByEntityEvent through CombatDispatch — gather the
// attacker/defender candidate abilities, run them through the executor into a Sink, fold the damage
// onto the event once, and flush. No business logic that isn't a call into engine/item (§2, §2.1).
