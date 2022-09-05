package dev.emortal.marathon.utils

import net.minestom.server.adventure.audience.PacketGroupingAudience
import net.minestom.server.coordinate.Point
import net.minestom.server.instance.block.Block
import net.minestom.server.network.packet.server.play.BlockBreakAnimationPacket
import net.minestom.server.network.packet.server.play.EffectPacket

//fun PacketGroupingAudience.setFakeBlock(point: Point, block: Block) {
//    sendGroupedPacket(BlockChangePacket(point, block.stateId().toInt()))
//}

var howManyBlocksBroken = 0

fun PacketGroupingAudience.sendBlockDamage(point: Point, destroyStage: Byte, blockIndex: Int? = null) {
    // one "player" can't break two blocks at once. but it doesn't care if they're real or not sooooo
    // mfw Int.MAX_VALUE players join emortal and breaks this
    sendGroupedPacket(BlockBreakAnimationPacket(Int.MAX_VALUE - (blockIndex ?: howManyBlocksBroken++), point, destroyStage))
}

fun PacketGroupingAudience.breakBlock(point: Point, block: Block) {
    sendGroupedPacket(EffectPacket(2001/*Block break + block break sound*/, point, block.stateId().toInt(), false))
}