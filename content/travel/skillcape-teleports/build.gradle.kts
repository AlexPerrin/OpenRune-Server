plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.generated)
    implementation(projects.api.player)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.utils.utilsTime)
    implementation(projects.engine.map)
}
