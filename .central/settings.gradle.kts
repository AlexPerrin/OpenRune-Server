pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "OpenRune-Central-Server"

include("central-common")
include("central-worldlink")
include("central-app")
include("openrune-central-admin-web")
