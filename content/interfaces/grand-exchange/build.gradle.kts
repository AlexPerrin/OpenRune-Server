plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(libs.rsprot.api)
}

testing.suites.named<JvmTestSuite>("integration") {
    dependencies {
        implementation(projects.api.pluginCommons)
        implementation(libs.rsprot.api)
    }
}
