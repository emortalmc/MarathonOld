package dev.emortal.marathon.utils

import net.minestom.server.adventure.audience.PacketGroupingAudience
import net.minestom.server.coordinate.Point
import net.minestom.server.instance.block.Block
import net.minestom.server.network.packet.server.play.BlockBreakAnimationPacket
import net.minestom.server.network.packet.server.play.BlockChangePacket

fun PacketGroupingAudience.setBlock(point: Point, block: Block) {
    val packet = BlockChangePacket()
    packet.blockPosition = point
    packet.blockStateId = block.stateId().toInt()

    this.sendGroupedPacket(packet)
}

fun PacketGroupingAudience.sendBlockDamage(point: Point, destroyStage: Byte) {
    val packet = BlockBreakAnimationPacket()
    packet.destroyStage = destroyStage
    packet.blockPosition = point
    packet.entityId = this.players.first().entityId

    this.sendGroupedPacket(packet)
}