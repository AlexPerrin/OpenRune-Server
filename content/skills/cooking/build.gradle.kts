plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.content.interfaces.achievementDiary)
    implementation(projects.api.registry)
    implementation(projects.content.skills.utils)
    implementation(projects.content.generic.genericLocs)
    implementation(projects.content.quest)
}
