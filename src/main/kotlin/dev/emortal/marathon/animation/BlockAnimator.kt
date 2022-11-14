package dev.emortal.marathon.animation

import dev.emortal.immortal.game.Game
import dev.emortal.immortal.util.MinestomRunnable
import dev.emortal.marathon.utils.sendBlockDamage
import net.kyori.adventure.sound.Sound
import net.minestom.server.coordinate.Point
import net.minestom.server.instance.block.Block
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import world.cepi.kstom.util.sendBreakBlockEffect
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

abstract class BlockAnimator {
    abstract fun setBlockAnimated(game: Game, point: Point, block: Block, lastPoint: Point)

    open fun destroyBlockAnimated(game: Game, point: Point, block: Block) {
        game.instance?.setBlock(point, Block.AIR)
    }
}