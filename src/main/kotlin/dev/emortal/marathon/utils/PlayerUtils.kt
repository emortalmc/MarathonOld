package dev.emortal.marathon.utils

import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.instance.block.Block
import net.minestom.server.network.packet.server.play.BlockBreakAnimationPacket
import net.minestom.server.network.packet.server.play.BlockChangePacket

fun Player.setBlock(point: Point, block: Block) {
    val packet = BlockChangePacket()
    packet.blockPosition = point.roundToBlock()
    packet.blockStateId = block.stateId().toInt()

    playerConnection.sendPacket(packet)
}

fun Player.sendBlockDamage(point: Point, destroyStage: Byte) {
    val packet = BlockBreakAnimationPacket()
    packet.destroyStage = destroyStage
    packet.blockPosition = point
    packet.entityId = entityId

    playerConnection.sendPacket(packet)
}