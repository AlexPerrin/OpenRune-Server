plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.attr)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.registry)
    implementation(projects.content.quest)
}

testing.suites.named<JvmTestSuite>("integration") {
    dependencies {
        implementation(projects.api.attr)
        implementation(projects.api.pluginCommons)
    }
}
