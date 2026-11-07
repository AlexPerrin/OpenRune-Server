package org.rsmod.api.net.rsprot.handlers

import net.rsprot.protocol.game.incoming.misc.client.MapBuildComplete
import org.rsmod.api.net.rsprot.RspClient
import org.rsmod.api.net.rsprot.sceneLoading
import org.rsmod.game.entity.Player

class MapBuildCompleteHandler : MessageHandler<MapBuildComplete> {
    override fun handle(player: Player, message: MapBuildComplete) {
        player.lastMapBuildComplete = player.currentMapClock
        (player.client as? RspClient)?.session?.ctx?.channel()?.sceneLoading = false
    }
}
