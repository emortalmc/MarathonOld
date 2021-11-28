package dev.emortal.marathon.utils

import net.minestom.server.adventure.audience.PacketGroupingAudience
import net.minestom.server.coordinate.Point
import net.minestom.server.instance.block.Block
import net.minestom.server.network.packet.server.play.BlockBreakAnimationPacket
import net.minestom.server.network.packet.server.play.BlockChangePacket

fun PacketGroupingAudience.setBlock(point: Point, block: Block) {
    sendGroupedPacket(BlockChangePacket(point, block.stateId().toInt()))
}

fun PacketGroupingAudience.sendBlockDamage(point: Point, destroyStage: Byte) {
    sendGroupedPacket(BlockBreakAnimationPacket(players.first().entityId, point, destroyStage))
}