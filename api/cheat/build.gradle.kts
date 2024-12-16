plugins {
    id("base-conventions")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.bundles.logging)
    implementation(projects.api.playerOutput)
    implementation(projects.engine.game)
}
