plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.content.skills.utils)
}

testing.suites.named<JvmTestSuite>("integration") {
    dependencies {
        implementation(projects.api.pluginCommons)
    }
}
