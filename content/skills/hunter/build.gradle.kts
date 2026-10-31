plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.content.interfaces.collectionLog)
    implementation(projects.content.other.pets)
    implementation(projects.content.quest)
    implementation(projects.api.utils.utilsSkills)
}
