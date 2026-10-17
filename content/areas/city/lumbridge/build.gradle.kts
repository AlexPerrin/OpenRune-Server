plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.content.interfaces.achievementDiary)
    implementation(projects.api.scriptAdvanced)
    implementation(projects.content.quest)
}
