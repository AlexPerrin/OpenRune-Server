plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.net)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.scriptAdvanced)
    testImplementation(projects.api.invPlugin)
    testImplementation(projects.api.invStorage)
}
