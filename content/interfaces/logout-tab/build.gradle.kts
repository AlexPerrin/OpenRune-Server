plugins {
    id("base-conventions")
}

dependencies {
    implementation(libs.openrune.central)
    implementation(libs.openrune.central.common)
    implementation(projects.api.db)
    implementation(projects.api.pluginCommons)
}
