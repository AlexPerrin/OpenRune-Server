plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.areaChecker)
}

testing.suites.named<JvmTestSuite>("integration") {
    dependencies {
        implementation(projects.api.pluginCommons)
    }
}
