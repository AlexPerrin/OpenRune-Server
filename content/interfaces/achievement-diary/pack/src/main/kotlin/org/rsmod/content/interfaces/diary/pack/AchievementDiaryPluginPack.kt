package org.rsmod.content.interfaces.diary.pack

import dev.openrune.definition.dbtables.DBTable
import dev.openrune.pack.PluginPack

class AchievementDiaryPluginPack : PluginPack() {
    override fun dbTables(): List<DBTable> =
        listOf(AchievementDiariesTable.diaries(), AchievementDiaryTasksTable.tasks())
}
