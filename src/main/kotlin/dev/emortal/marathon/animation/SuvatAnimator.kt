package dev.emortal.marathon.animation

import dev.emortal.immortal.game.Game
import dev.emortal.marathon.game.MarathonGame
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.other.FallingBlockMeta
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule
import kotlin.math.ceil
import kotlin.math.sqrt


class SuvatAnimator : BlockAnimator() {


    var lastValidBlock: Point? = null

    override fun onReset(game: Game) {
        lastValidBlock = null
    }

    override fun setBlockAnimated(game: Game, point: Point, block: Block, lastPoint: Point) {
        if (lastValidBlock == null) lastValidBlock = lastPoint

        val h = 2.2 // height to aim for
        val gravity = -0.06

        val realLastPoint = lastValidBlock!!.add(0.5, 0.0, 0.5)

        val lastEntity = NoPhysicsEntity(EntityType.FALLING_BLOCK);
//        lastEntity.setTag(MarathonGame.MARATHON_ENTITY_TAG, true)
//        lastEntity.setDrag(false)
//        lastEntity.setPhysics(false)

        lastEntity.setGravity(0.0, -gravity);

        val meta = lastEntity.entityMeta as FallingBlockMeta
        meta.block = block

        val displacementY = point.y() - realLastPoint.y()
        val displacementXZ = point.sub(realLastPoint)
        val time = sqrt(-2 * h / gravity) + sqrt(2 * (displacementY - h) / gravity)
        val velocityY = sqrt(-2 * gravity * h)
        val velocityXZ = displacementXZ.div(time)

        val combinedVelocity = Vec.fromPoint(velocityXZ.withY(velocityY))
        lastEntity.velocity = combinedVelocity.mul(MinecraftServer.TICK_PER_SECOND.toDouble())

        lastEntity.setInstance(game.instance!!, realLastPoint);

        val finalEntity = lastEntity
        lastEntity.scheduler().buildTask {
            finalEntity.remove()
            game.instance!!.setBlock(point, block);
            lastValidBlock = point
        }.delay(TaskSchedule.tick((ceil(time) - 1).coerceAtLeast(1.0).toInt())).schedule()
    }

    override fun destroyBlockAnimated(game: Game, point: Point, block: Block) {
        game.instance!!.setBlock(point, Block.AIR);
    }
}
