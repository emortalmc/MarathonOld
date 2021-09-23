package dev.emortal.marathon.game

import dev.emortal.marathon.BlockPalette
import dev.emortal.marathon.utils.firsts
import dev.emortal.marathon.utils.roundToBlock
import dev.emortal.marathon.utils.sendBlockDamage
import dev.emortal.marathon.utils.setBlock
import emortal.immortal.game.Game
import emortal.immortal.game.GameOptions
import emortal.immortal.particle.ParticleUtils
import emortal.immortal.particle.shapes.sendParticle
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerChangeHeldSlotEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.Task
import net.minestom.server.utils.time.TimeUnit
import world.cepi.kstom.Manager
import world.cepi.kstom.event.listenOnly
import world.cepi.kstom.util.playSound
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.pow
import kotlin.math.roundToInt

class MarathonGame(gameOptions: GameOptions) : Game(gameOptions) {

    companion object {
        val SPAWN_POINT = Pos(0.5, 150.0, 0.5)
        val DATE_FORMAT = SimpleDateFormat("mm:ss")
    }

    private val player: Player
        get() = players.first()

    var targetY = 150
    var targetX = 0

    var currentBreakingTask: Task? = Manager.scheduler.buildTask { updateActionBar() }.repeat(Duration.ofSeconds(1)).schedule()
    var currentBreakingProgress = 0
    var finalBlockPos: Point = Pos(0.0, 149.0, 0.0)

    // Controls the amount of blocks infront of the player
    val length = 8

    var lastBlockTimestamp = 0L
    var startTimestamp = 0L

    var score = 0
    var combo = 0
    var blockPalette = BlockPalette.DEFAULT
        set(value) {
            if (blockPalette == value) return

            for (block in blocks) {
                player.setBlock(block.first, value.blocks.random())
            }

            field = value
        }

    var blocks = mutableListOf<Pair<Point, Block>>()

    override fun startCountdown() {

    }

    override fun playerJoin(player: Player) {
        player.respawnPoint = SPAWN_POINT
        if (player.instance!! != instance) {
            println("changed instance")
            player.setInstance(instance)
        }
        start()
    }

    override fun playerLeave(player: Player) {
        destroy()
    }

    override fun registerEvents() {
        eventNode.listenOnly<PlayerMoveEvent> {
            if (newPosition.y() < 130) {
                reset()
                return@listenOnly
            }

            val posUnderPlayer = newPosition.sub(0.0, 1.0, 0.0).roundToBlock()
            val pos2UnderPlayer = newPosition.sub(0.0, 2.0, 0.0).roundToBlock()

            val blockPositions = blocks.firsts()

            if (blockPositions.contains(posUnderPlayer) || blockPositions.contains(pos2UnderPlayer)) {
                var index = blockPositions.indexOf(pos2UnderPlayer)

                if (index == -1) index = blockPositions.indexOf(posUnderPlayer)
                if (index == -1 || index == 0) return@listenOnly

                generateNextBlock(index, true)
            }
        }

        eventNode.listenOnly<PlayerChangeHeldSlotEvent> {
            //val palette: BlockPalette = blockPaletteStore.getPalette(slot) ?: return@listenOnly
            //blockPalette = palette
        }
    }

    override fun start() {
        startTimestamp = System.currentTimeMillis()
        reset()
    }

    fun reset() {
        blocks.forEach {
            player.setBlock(it.first, Block.AIR)
        }
        blocks.clear()

        currentBreakingTask?.cancel()
        currentBreakingTask = null

        score = 0
        combo = 0
        finalBlockPos = Pos(0.0, 149.0, 0.0)

        blocks.add(Pair(finalBlockPos, Block.DIAMOND_BLOCK))

        player.velocity = Vec.ZERO
        player.teleport(SPAWN_POINT)
        player.setBlock(finalBlockPos, Block.DIAMOND_BLOCK)

        generateNextBlock(length, false)

        startTimestamp = System.currentTimeMillis()
    }

    fun generateNextBlock(times: Int, inGame: Boolean) {
        if (inGame) {
            val powerResult = 2.0.pow(combo / 30.0)
            val maxTimeTaken = 1000L * times / powerResult

            if (System.currentTimeMillis() - lastBlockTimestamp < maxTimeTaken) combo += times else combo = 0

            val basePitch: Float = 1 + (combo - 1) * 0.05f

            score += times

            playSound(basePitch)
            createBreakingTask()
            updateActionBar()
        }
        repeat(times) { generateNextBlock() }
        lastBlockTimestamp = System.currentTimeMillis()
    }

    fun generateNextBlock() {
        if (blocks.size > length) {
            player.setBlock(blocks[0].first, Block.AIR)
            blocks.removeAt(0)
        }

        if (finalBlockPos.y() == 150.0) targetY = 0 else if (finalBlockPos.y() < 135 || finalBlockPos.y() > 240) targetY = 150

        val rand = ThreadLocalRandom.current()

        val y = if (targetY == 0) rand.nextInt(-1, 2) else if (targetY < finalBlockPos.y()) -1 else 1
        val z = if (y == 1) rand.nextInt(1, 4) else if (y == -1) rand.nextInt(1, 6) else rand.nextInt(1, 5)
        val x = rand.nextInt(-3, 4)

        println("finalbp y = ${finalBlockPos.y()} block y = ${finalBlockPos.blockY()}")
        finalBlockPos = finalBlockPos.add(x.toDouble(), y.toDouble(), z.toDouble())

        val newPaletteBlock = blockPalette.blocks.random()
        val newPaletteBlockPos = finalBlockPos

        player.setBlock(finalBlockPos, newPaletteBlock)
        blocks.add(Pair(newPaletteBlockPos, newPaletteBlock))

        player.sendParticle(ParticleUtils.particle(Particle.INSTANT_EFFECT, finalBlockPos.x(), finalBlockPos.y(), finalBlockPos.z(), 0.6f, 0.6f, 0.6f, 15))
    }

    private fun createBreakingTask() {
        currentBreakingProgress = 0

        currentBreakingTask?.cancel()
        currentBreakingTask = MinecraftServer.getSchedulerManager().buildTask {
            if (blocks.size < 1) return@buildTask

            val block = blocks[0]

            currentBreakingProgress += 2

            if (currentBreakingProgress > 8) {
                //ParticleUtils.sendParticle(player, Particle.BLOCK, blocks.get(0).toPosition(), 0.5f, 0.5f, 0.5f, 5);
                player.playSound(Sound.sound(SoundEvent.BLOCK_WOOD_BREAK, Sound.Source.BLOCK, 1f, 1f), block.first)
                player.setBlock(block.first, Block.AIR)

                generateNextBlock()
                createBreakingTask()

                return@buildTask
            }

            player.playSound(Sound.sound(SoundEvent.BLOCK_WOOD_HIT, Sound.Source.BLOCK, 0.5f, 1f), block.first)
            player.sendBlockDamage(block.first, currentBreakingProgress.toByte())
        }.delay(1000, TimeUnit.MILLISECOND).repeat(500, TimeUnit.MILLISECOND).schedule()
    }

    private fun updateActionBar() {
        val millisTaken: Long = if (startTimestamp == -1L) 0 else System.currentTimeMillis() - startTimestamp
        val formattedTime: String = DATE_FORMAT.format(Date(millisTaken))
        val secondsTaken = Math.max(millisTaken / 1000.0, 1.0)
        val scorePerSecond = (score / secondsTaken * 100).roundToInt() / 100.0

        val message: Component = Component.text()
            .append(Component.text(score, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
            .append(Component.text(" points", NamedTextColor.DARK_PURPLE))
            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
            .append(Component.text(formattedTime, NamedTextColor.GRAY))
            .build()

        player.sendActionBar(message)
    }

    fun playSound(pitch: Float) {
        player.playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 3f, pitch), player.position)
    }

}