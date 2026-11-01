plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    testImplementation(projects.api.invPlugin)
    testImplementation(projects.api.gameProcess)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.api.registry)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.repo)
    implementation(projects.content.quest)
    implementation(projects.content.skills.runecrafting)
}
