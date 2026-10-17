plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.content.interfaces.emotes)
    implementation(projects.api.death)
    implementation(projects.api.pluginCommons)
}

testing.suites.named<JvmTestSuite>("integration") {
    dependencies {
        implementation(projects.api.pluginCommons)
    }
}
