package dev.emortal.marathon.animation

import dev.emortal.immortal.game.Game
import dev.emortal.immortal.util.MinestomRunnable
import dev.emortal.immortal.util.TaskGroup
import dev.emortal.marathon.utils.breakBlock
import dev.emortal.marathon.utils.sendBlockDamage
import net.kyori.adventure.sound.Sound
import net.minestom.server.coordinate.Point
import net.minestom.server.instance.block.Block
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import world.cepi.kstom.util.playSound
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

abstract class BlockAnimator(val game: Game) {
    val taskGroup = TaskGroup()
    val blocksAndTasks = mutableSetOf<Pair<Point, MinestomRunnable>>()
    companion object {
        val indexTag = Tag.Integer("blockIndex")
    }

    val tasks: MutableSet<Task> = ConcurrentHashMap.newKeySet()

    abstract fun setBlockAnimated(point: Point, block: Block, lastPoint: Point)

    // would've been nice to just override cancel() but whatever
    open fun flushBreakAnimations() {
        for ((i, blockAndTask) in blocksAndTasks.withIndex()) {
            blockAndTask.second.cancel()
            // have to reset breaking state to prevent "ghost breakage"
            game.instance.get()?.sendBlockDamage(blockAndTask.first, -1, i)
            game.instance.get()?.setBlock(blockAndTask.first, Block.AIR)
        }
        blocksAndTasks.clear()
    }

    open fun destroyBlockAnimated(block: Point, feedback: Boolean = false) {

        // Remove all tasks breaking this block already
        blocksAndTasks.removeIf {
            val remove = it.first.sameBlock(block)
            if (remove) it.second.cancel()
            return@removeIf remove
        }

        // Add the block to the set, then start a task to break it
        blocksAndTasks.add(Pair(block,
            object : MinestomRunnable(
                taskGroup = taskGroup,
                delay = Duration.ZERO,
                repeat = Duration.ofMillis(250)
            ) {
                var currentBreakingProgress = 0
                val instance = game.instance.get()

                override fun run() {
                    // Increase the amount the block is broken
                    currentBreakingProgress += 1

                    // When it hits 8,
                    if (currentBreakingProgress > 8) {
                        // break the block!
                        if (feedback) {
                            instance?.breakBlock(block, instance.getBlock(block))
                        }
                        instance?.setBlock(block, Block.AIR)
                        // And cancel this task
                        cancel()
                        return
                    }

                    // Sound and block breaking progress animation
                    if (feedback) {
                        instance?.playSound(Sound.sound(SoundEvent.BLOCK_WOOL_HIT, Sound.Source.BLOCK, 0.5f, 1f), block)
                    }
                    instance?.sendBlockDamage(block, currentBreakingProgress.toByte(), blocksAndTasks.indexOfFirst { it.second == this })
                }
            }
        ))
    }

    open fun destroyBlockAnimated(point: Point, block: Block) {
        game.instance.get()?.setBlock(point, Block.AIR)
    }
}