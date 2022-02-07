package dev.emortal.marathon.game

import dev.emortal.immortal.game.Game
import dev.emortal.immortal.game.GameOptions
import dev.emortal.marathon.MarathonExtension
import dev.emortal.marathon.SoundSettingGUI
import dev.emortal.marathon.animation.BlockAnimator
import dev.emortal.marathon.animation.FallingSandAnimator
import dev.emortal.marathon.db.Highscore
import dev.emortal.marathon.generator.Generator
import dev.emortal.marathon.generator.LegacyGenerator
import dev.emortal.marathon.utils.*
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerChangeHeldSlotEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.scoreboard.Sidebar
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.utils.NamespaceID
import net.minestom.server.utils.time.TimeUnit
import world.cepi.kstom.Manager
import world.cepi.kstom.adventure.noItalic
import world.cepi.kstom.event.listenOnly
import world.cepi.kstom.item.item
import world.cepi.kstom.util.asPos
import world.cepi.kstom.util.asVec
import world.cepi.kstom.util.playSound
import world.cepi.kstom.util.roundToBlock
import world.cepi.particle.Particle
import world.cepi.particle.ParticleType
import world.cepi.particle.data.Color
import world.cepi.particle.data.OffsetAndSpeed
import world.cepi.particle.extra.Dust
import world.cepi.particle.renderer.Renderer
import world.cepi.particle.showParticle
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import kotlin.math.pow

class MarathonGame(gameOptions: GameOptions) : Game(gameOptions) {

    companion object {
        val SPAWN_POINT = Pos(0.5, 150.0, 0.5)
        val DATE_FORMAT = SimpleDateFormat("mm:ss")

        val paletteTag = Tag.String("palette")

        val soundSettingsInventory = SoundSettingGUI()
    }

    val generator: Generator = LegacyGenerator
    val animation: BlockAnimator = FallingSandAnimator(this)
    var pointSoundEffect: SoundEvent = SoundEvent.BLOCK_NOTE_BLOCK_PLING

    var targetY = 150
    var targetX = 0

    var breakingTask: Task? = null
    var currentBreakingProgress = 0

    var actionBarTask: Task? = null

    var finalBlockPos: Point = Pos(0.0, 149.0, 0.0)

    // Amount of blocks in front of the player
    var length = 8

    var lastBlockTimestamp = 0L
    var startTimestamp = -1L

    var score = 0
    var combo = 0

    var blockPalette = BlockPalette.GRASS
        set(value) {
            if (blockPalette == value) return

            playSound(Sound.sound(value.soundEffect, Sound.Source.MASTER, 1f, 1f), Sound.Emitter.self())

            for (block in blocks) {
                setBlock(block.first, value.blocks.random())
            }

            field = value
        }

    var blocks = mutableListOf<Pair<Point, Block>>()

    override var spawnPosition = SPAWN_POINT

    init {
        scoreboard?.createLine(
            Sidebar.ScoreboardLine(
                "highscoreLine",
                Component.text()
                    .append(Component.text("Highscore: ", NamedTextColor.GRAY))
                    .append(Component.text(0, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                    .build(),
                0
            )
        )
    }

    override fun playerJoin(player: Player) = runBlocking {
        scoreboard?.updateLineContent(
            "highscoreLine",
            Component.text()
                .append(Component.text("Highscore: ", NamedTextColor.GRAY))
                .append(Component.text(MarathonExtension.storage?.getHighscoreAsync(player.uuid)?.score ?: 0, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                .build()
        )

        BlockPalette.values().forEachIndexed { i, it ->
            val item = item(it.displayItem) {
                setTag(paletteTag, it.name)
                displayName(it.displayName.noItalic())
            }

            player.inventory.setItemStack(i + 2, item)
        }
        player.inventory.setItemStack(8, ItemStack.of(Material.NOTE_BLOCK))
    }

    override fun playerLeave(player: Player) {
        destroy()
    }

    override fun registerEvents() {
        eventNode.listenOnly<PlayerUseItemEvent> {
            if (this.itemStack.material == Material.NOTE_BLOCK) {
                this.isCancelled = true
                player.openInventory(soundSettingsInventory.inventory)
            }
        }

        eventNode.listenOnly<PlayerMoveEvent> {
            if (newPosition.y() < (blocks.map { it.first }.minOf { it.y() }) - 3) {
                reset()
                return@listenOnly
            }

            val posUnderPlayer = newPosition.sub(0.0, 1.0, 0.0).roundToBlock().asPos()
            val pos2UnderPlayer = newPosition.sub(0.0, 2.0, 0.0).roundToBlock().asPos()

            val blockPositions = blocks.firsts()

            var index = blockPositions.indexOf(pos2UnderPlayer)

            if (index == -1) index = blockPositions.indexOf(posUnderPlayer)
            if (index == -1 || index == 0) return@listenOnly

            generateNextBlock(index, true)
        }

        eventNode.listenOnly<PlayerChangeHeldSlotEvent> {
            val palette = BlockPalette.valueOf(
                player.inventory.getItemStack(slot.toInt()).getTag(paletteTag) ?: return@listenOnly
            )

            if (blockPalette == palette) return@listenOnly

            blockPalette = palette
        }
    }

    override fun gameStarted() {
        startTimestamp = System.currentTimeMillis()
        scoreboard?.removeLine("infoLine")
        reset()
    }

    override fun gameDestroyed() {
        breakingTask?.cancel()
        actionBarTask?.cancel()
    }

    private fun reset() = runBlocking {
        blocks.forEach {
            if (it.first == Block.DIAMOND_BLOCK) return@forEach
            setBlock(it.first, Block.AIR)
        }
        blocks.clear()

        instance.entities.filter { it !is Player }.forEach { it.remove() }

        breakingTask?.cancel()
        breakingTask = null

        val previousScore = score
        val highscore = MarathonExtension.storage?.getHighscoreAsync(players.first().uuid)?.score ?: 0

        if (previousScore == highscore && previousScore != 0) {
            players.first().showTitle(
                Title.title(
                    Component.text("F", NamedTextColor.RED, TextDecoration.BOLD),
                    Component.empty(),
                    Title.Times.of(Duration.ofSeconds(3), Duration.ofSeconds(2), Duration.ofSeconds(1))
                )
            )

            val millisTaken = System.currentTimeMillis() - startTimestamp
            val formattedTime: String = DATE_FORMAT.format(Date(millisTaken))

            players.first().sendMessage(
                Component.text()
                    .append(Component.text("\nYou didn't beat your previous highscore of ", NamedTextColor.GRAY))
                    .append(Component.text(highscore, NamedTextColor.GREEN))
                    .append(Component.text(" in ", NamedTextColor.GRAY))
                    .append(Component.text(formattedTime, NamedTextColor.GOLD))
                    .append(Component.text(" because you literally died one block before", NamedTextColor.GRAY))
                    .append(Component.text("\nWe can't even show the new highscore, because you didn't get one", NamedTextColor.LIGHT_PURPLE))
                    .armify()
            )

            scoreboard?.updateLineContent(
                "highscoreLine",
                Component.text()
                    .append(Component.text("Your not Highscore: ", NamedTextColor.GRAY))
                    .append(Component.text(previousScore, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                    .build()
            )
        }

        if (previousScore > highscore) {
            val millisTaken = System.currentTimeMillis() - startTimestamp
            val formattedTime: String = DATE_FORMAT.format(Date(millisTaken))

            players.first().sendMessage(
                Component.text()
                    .append(Component.text("\nYou beat your previous highscore of ", NamedTextColor.GRAY))
                    .append(Component.text(highscore, NamedTextColor.GREEN))
                    .append(Component.text(" by ", NamedTextColor.GRAY))
                    .append(Component.text("${previousScore - highscore} points", NamedTextColor.GREEN))
                    .append(Component.text(" in ", NamedTextColor.GRAY))
                    .append(Component.text(formattedTime, NamedTextColor.GOLD))
                    .append(Component.text("\nYour new highscore is ", NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text(previousScore, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                    .append(Component.text("\n"))
                    .armify()
            )

            scoreboard?.updateLineContent(
                "highscoreLine",
                Component.text()
                    .append(Component.text("Highscore: ", NamedTextColor.GRAY))
                    .append(Component.text(previousScore, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                    .build()
            )

            MarathonExtension.storage?.setHighscore(players.first().uuid, Highscore(previousScore, millisTaken))
        }

        score = 0
        combo = 0
        finalBlockPos = Pos(0.0, 149.0, 0.0)

        blocks.add(Pair(finalBlockPos, Block.DIAMOND_BLOCK))

        players.forEach {
            it.velocity = Vec.ZERO
            it.teleport(SPAWN_POINT)
        }

        setBlock(finalBlockPos, Block.DIAMOND_BLOCK)

        generateNextBlock(length, false)

        startTimestamp = -1
        updateActionBar()
    }

    fun generateNextBlock(times: Int, inGame: Boolean) {
        if (inGame) {
            if (startTimestamp == -1L) {
                actionBarTask = Manager.scheduler.buildTask { updateActionBar() }
                    .repeat(Duration.ofSeconds(1))
                    .schedule()
                startTimestamp = System.currentTimeMillis()
            }

            val powerResult = 2.0.pow(combo / 30.0)
            val maxTimeTaken = 1000L * times / powerResult

            if (System.currentTimeMillis() - lastBlockTimestamp < maxTimeTaken) combo += times else combo = 0

            val basePitch: Float = 1 + (combo - 1) * 0.05f

            score += times
            showTitle(
                Title.title(
                    Component.empty(),
                    Component.text(score, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                    Title.Times.of(Duration.ZERO, Duration.ofMillis(200), Duration.ofMillis(200))
                )
            )

            playSound(basePitch)
            createBreakingTask()
            updateActionBar()
        }
        repeat(times) { generateNextBlock() }
        lastBlockTimestamp = System.currentTimeMillis()
    }

    fun generateNextBlock() {
        if (blocks.size > length) {
            animation.destroyBlockAnimated(blocks[0].first, blocks[0].second)

            blocks.removeAt(0)
        }

        if (finalBlockPos.y() == 150.0) targetY =
            0 else if (finalBlockPos.y() < 135 || finalBlockPos.y() > 240) targetY = 150

        finalBlockPos = generator.getNextPosition(finalBlockPos, targetX, targetY, score)

        val newPaletteBlock = blockPalette.blocks.random()
        val newPaletteBlockPos = finalBlockPos

        val lastBlock = blocks.last()
        animation.setBlockAnimated(newPaletteBlockPos, newPaletteBlock, lastBlock.first, lastBlock.second)

        blocks.add(Pair(newPaletteBlockPos, newPaletteBlock))

        instance.showParticle(
            Particle.particle(
                type = ParticleType.DUST,
                count = 1,
                data = OffsetAndSpeed(),
                extraData = Dust(1f, 1f, 1f, 1f)
            ), Renderer.fixedRectangle(finalBlockPos.asVec(), finalBlockPos.asVec().add(1.0, 1.0, 1.0), step = 0.15)
        )
    }

    private fun createBreakingTask() {
        currentBreakingProgress = 0

        breakingTask?.cancel()
        breakingTask = MinecraftServer.getSchedulerManager().buildTask {
            if (blocks.size < 1) return@buildTask

            val block = blocks[0]

            currentBreakingProgress += 2

            if (currentBreakingProgress > 8) {
                generateNextBlock()
                createBreakingTask()

                return@buildTask
            }

            playSound(Sound.sound(SoundEvent.BLOCK_WOOD_HIT, Sound.Source.BLOCK, 0.5f, 1f), block.first)
            sendBlockDamage(block.first, currentBreakingProgress.toByte())
        }.delay(2000, TimeUnit.MILLISECOND).repeat(500, TimeUnit.MILLISECOND).schedule()
    }

    private fun updateActionBar() {
        val millisTaken: Long = if (startTimestamp == -1L) 0 else System.currentTimeMillis() - startTimestamp
        val formattedTime: String = DATE_FORMAT.format(Date(millisTaken))
        //val secondsTaken = (millisTaken / 1000.0).coerceAtLeast(1.0)
        //val scorePerSecond = (score / secondsTaken * 100).roundToInt() / 100.0

        val message: Component = Component.text()
            .append(Component.text(score, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
            .append(Component.text(" points", NamedTextColor.DARK_PURPLE))
            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
            .append(Component.text(formattedTime, NamedTextColor.GRAY))
            .build()

        sendActionBar(message)
    }

    fun playSound(pitch: Float) {
        playSound(
            Sound.sound(
                pointSoundEffect,
                Sound.Source.MASTER,
                3f,
                pitch
            ),
            Sound.Emitter.self()
        )
    }

    // Marathon is not winnable
    override fun victory(winningPlayers: Collection<Player>) {
    }

    override fun instanceCreate(): Instance {
        //val sharedInstance = Manager.instance.createSharedInstance(MarathonExtension.parkourInstance)
        //sharedInstance.time = 0
        //sharedInstance.timeRate = 0

        //return sharedInstance

        val dimension = Manager.dimensionType.getDimension(NamespaceID.from("fullbright"))!!
        val newInstance = Manager.instance.createInstanceContainer(dimension)
        newInstance.time = 0
        newInstance.timeRate = 0
        newInstance.setBlock(0, 149, 0, Block.DIAMOND_BLOCK)

        return newInstance
    }

}