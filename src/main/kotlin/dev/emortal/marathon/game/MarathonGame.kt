package dev.emortal.marathon.game

import dev.emortal.immortal.config.GameOptions
import dev.emortal.immortal.game.Game
import dev.emortal.marathon.MarathonExtension
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
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.player.PlayerChangeHeldSlotEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Enchantment
import net.minestom.server.item.ItemHideFlag
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
import world.cepi.particle.data.OffsetAndSpeed
import world.cepi.particle.showParticle
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import kotlin.math.pow
import kotlin.math.roundToInt

class MarathonGame(gameOptions: GameOptions) : Game(gameOptions) {

    companion object {
        val SPAWN_POINT = Pos(0.5, 150.0, 0.5)
        val dateFormat = SimpleDateFormat("mm:ss")
        val accurateDateFormat = SimpleDateFormat("mm:ss.SSS")

        val paletteTag = Tag.Integer("palette")
    }

    val generator: Generator = LegacyGenerator
    val animation: BlockAnimator = FallingSandAnimator(this)

    // Amount of blocks in front of the player
    var length = 8
    var targetY = 150
    var targetX = 0

    var actionBarTask: Task? = null
    var breakingTask: Task? = null
    var currentBreakingProgress = 0

    var finalBlockPos: Point = Pos(0.0, 149.0, 0.0)

    var lastBlockTimestamp = 0L
    var startTimestamp = -1L

    var score = -1
    var combo = 0
    var target = -1

    private var highscore: Highscore? = null
    private var passedHighscore = false
    var passedTarget = true

    var blockPalette = BlockPalette.OVERWORLD
        set(value) {
            if (blockPalette == value) return

            playSound(Sound.sound(value.soundEffect, Sound.Source.MASTER, 1f, 1f), Sound.Emitter.self())

            blocks.forEachIndexed { i, block ->
                val newBlock = value.blocks.random()
                instance.setBlock(block.first, newBlock)
                blocks[i] = Pair(block.first, newBlock)
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
        scoreboard?.createLine(
            Sidebar.ScoreboardLine(
                "placementLine",
                Component.text()
                    .append(Component.text("#-", NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text(" on leaderboard", NamedTextColor.GRAY))
                    .build(),
                0
            )
        )
    }

    override fun playerJoin(player: Player) = runBlocking {
        highscore = MarathonExtension.storage?.getHighscoreAsync(player.uuid)
        val highscorePoints = highscore?.score ?: 0
        val placement = MarathonExtension.storage?.getPlacementAsync(highscorePoints) ?: 0

        scoreboard?.updateLineContent(
            "highscoreLine",
            Component.text()
                .append(Component.text("Highscore: ", NamedTextColor.GRAY))
                .append(Component.text(highscorePoints, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                .build()
        )
        scoreboard?.updateLineContent(
            "placementLine",
            Component.text()
                .append(Component.text("#${placement}", NamedTextColor.GOLD))
                .append(Component.text(" on leaderboard", NamedTextColor.GRAY))
                .build()
        )

        BlockPalette.values().forEachIndexed { i, it ->
            val item = item(it.displayItem) {
                setTag(paletteTag, it.ordinal)
                displayName(it.displayName.noItalic())
            }

            player.inventory.setItemStack(i + 2, item)
        }
        player.inventory.setItemStack(8, item(Material.MUSIC_DISC_BLOCKS) {
            displayName(Component.text("Music", NamedTextColor.GOLD).noItalic())
            enchantment(Enchantment.INFINITY, 1)
            hideFlag(ItemHideFlag.HIDE_ENCHANTS)
        })
    }

    override fun playerLeave(player: Player) {
        destroy()
    }

    override fun registerEvents() = with(eventNode) {
        listenOnly<ItemDropEvent> {
            isCancelled = true
        }
        listenOnly<InventoryPreClickEvent> {
            isCancelled = true
        }

        listenOnly<PlayerUseItemEvent> {
            if (this.itemStack.material == Material.MUSIC_DISC_BLOCKS) {
                this.isCancelled = true
                player.chat("/music")
            }
        }

        listenOnly<PlayerMoveEvent> {
            if (newPosition.y() < (blocks.map { it.first }.minOfOrNull { it.y() } ?: 3.0) - 3) {
                player.teleport(SPAWN_POINT)
                reset()
            }

            val posUnderPlayer = newPosition.sub(0.0, 1.0, 0.0).roundToBlock().asPos()
            val pos2UnderPlayer = newPosition.sub(0.0, 2.0, 0.0).roundToBlock().asPos()

            val blockPositions = blocks.firsts()

            var index = blockPositions.indexOf(pos2UnderPlayer)

            if (index == -1) index = blockPositions.indexOf(posUnderPlayer)
            if (index == -1 || index == 0) return@listenOnly

            generateNextBlock(index, true)
        }

        listenOnly<PlayerChangeHeldSlotEvent> {
            val palette = BlockPalette.values().get(player.inventory.getItemStack(slot.toInt()).getTag(paletteTag) ?: return@listenOnly)

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

    override fun spectatorJoin(player: Player) {
        player.teleport(players.first().position)
    }

    private fun reset() = runBlocking {
        if (score == 0) return@runBlocking

        blocks.forEach {
            if (it.first == Block.DIAMOND_BLOCK) return@forEach
            instance.setBlock(it.first, Block.AIR)
        }
        blocks.clear()

        score = 0
        combo = 0
        finalBlockPos = Pos(0.0, 149.0, 0.0)

        blocks.add(Pair(finalBlockPos, Block.DIAMOND_BLOCK))
        instance.setBlock(finalBlockPos, Block.DIAMOND_BLOCK)
        generateNextBlock(length, false)

        animation.tasks.forEach {
            it.cancel()
        }
        animation.tasks.clear()

        instance.entities.filter { it !is Player }.forEach { it.remove() }

        breakingTask?.cancel()
        breakingTask = null

        val previousScore = score

        val highscoreScore = highscore?.score ?: 0
        if (previousScore == highscoreScore && previousScore != 0) {
            showTitle(
                Title.title(
                    Component.text("F", NamedTextColor.RED, TextDecoration.BOLD),
                    Component.empty(),
                    Title.Times.of(Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(1))
                )
            )

            val millisTaken = System.currentTimeMillis() - startTimestamp
            val formattedTime: String = dateFormat.format(Date(millisTaken))

            sendMessage(
                Component.text()
                    .append(Component.text("\nYou didn't beat your previous highscore of ", NamedTextColor.GRAY))
                    .append(Component.text(highscoreScore, NamedTextColor.GREEN))
                    .append(Component.text(" in ", NamedTextColor.GRAY))
                    .append(Component.text(formattedTime, NamedTextColor.GOLD))
                    .append(Component.text(" because you literally died one block before", NamedTextColor.GRAY))
                    .append(Component.text("\nWe can't even show the new highscore, because you didn't get one (L)", NamedTextColor.LIGHT_PURPLE))
                    .armify()
            )

            scoreboard?.updateLineContent(
                "highscoreLine",
                Component.text()
                    .append(Component.text("Your (not) Highscore: ", NamedTextColor.GRAY))
                    .append(Component.text(previousScore, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                    .build()
            )
        }

        if (previousScore > highscoreScore) {
            val millisTaken = System.currentTimeMillis() - startTimestamp
            val formattedTime: String = dateFormat.format(Date(millisTaken))

            val placement = MarathonExtension.storage?.getPlacementAsync(previousScore) ?: 0

            sendMessage(
                Component.text()
                    .append(Component.text("\nYou beat your previous highscore of ", NamedTextColor.GRAY))
                    .append(Component.text(highscoreScore, NamedTextColor.GREEN))
                    .append(Component.text(" by ", NamedTextColor.GRAY))
                    .append(Component.text("${previousScore - highscoreScore} points", NamedTextColor.GREEN))
                    .append(Component.text(" in ", NamedTextColor.GRAY))
                    .append(Component.text(formattedTime, NamedTextColor.GOLD))
                    .append(Component.text("\nYour new highscore is ", NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text(previousScore, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                    .append(Component.text("\n\nYou are now #${placement} on the leaderboard!", NamedTextColor.GOLD))
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
            scoreboard?.updateLineContent(
                "placementLine",
                Component.text()
                    .append(Component.text("#${placement}", NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text(" on leaderboard", NamedTextColor.GRAY))
                    .build()
            )

            val newHighscoreObject = Highscore(previousScore, millisTaken)
            highscore = newHighscoreObject
            MarathonExtension.storage?.setHighscore(players.first().uuid, newHighscoreObject)
        }

        passedHighscore = false
        passedTarget = false

        getPlayers().forEach {
            it.velocity = Vec.ZERO
            it.teleport(SPAWN_POINT)
        }

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

            if (target != -1 && !passedTarget && score >= target) {
                passedTarget = true

                val millisTaken: Long = if (startTimestamp == -1L) 0 else System.currentTimeMillis() - startTimestamp
                val formattedTime: String = accurateDateFormat.format(Date(millisTaken))

                playSound(Sound.sound(SoundEvent.ENTITY_PLAYER_LEVELUP, Sound.Source.MASTER, 0.5f, 2f))
                sendMessage(
                    Component.text()
                        .append(Component.text("You met your target score in ", NamedTextColor.GRAY))
                        .append(Component.text(formattedTime, NamedTextColor.LIGHT_PURPLE))
                )
            }

            val highscorePoints = highscore?.score ?: 0
            if (!passedHighscore && score > highscorePoints) {
                sendMessage(
                    Component.text()
                        .append(Component.text("\nYou beat your previous highscore of ", NamedTextColor.GRAY))
                        .append(Component.text(highscorePoints, NamedTextColor.GREEN))
                        .append(Component.text("\nSee how much further you can go!", NamedTextColor.GOLD))
                        .append(Component.text("\n"))
                        .armify()
                )
                playSound(Sound.sound(SoundEvent.ENTITY_VILLAGER_CELEBRATE, Sound.Source.MASTER, 1f, 1f))

                passedHighscore = true
            }

            showTitle(
                Title.title(
                    Component.empty(),
                    Component.text(score, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                    Title.Times.of(Duration.ZERO, Duration.ofMillis(500), Duration.ofMillis(100))
                )
            )

            playSound(basePitch)
            createBreakingTask()
            updateActionBar()
        }
        repeat(times) { generateNextBlock(inGame) }
        lastBlockTimestamp = System.currentTimeMillis()
    }

    fun generateNextBlock(inGame: Boolean = true) {
        if (blocks.size > length) {
            animation.destroyBlockAnimated(blocks[0].first)

            blocks.removeAt(0)
        }

        if (finalBlockPos.y() == 150.0) targetY =
            0 else if (finalBlockPos.y() < 135 || finalBlockPos.y() > 240) targetY = 150

        finalBlockPos = generator.getNextPosition(finalBlockPos, targetX, targetY, score)

        val newPaletteBlock = blockPalette.blocks.random()
        val newPaletteBlockPos = finalBlockPos

        val lastBlock = blocks.last()
        if (inGame) animation.setBlockAnimated(newPaletteBlockPos, newPaletteBlock, lastBlock.first)
        else instance.setBlock(newPaletteBlockPos, newPaletteBlock)

        blocks.add(Pair(newPaletteBlockPos, newPaletteBlock))

        instance.showParticle(
            Particle.particle(
                type = ParticleType.CLOUD,
                count = 10,
                data = OffsetAndSpeed(0.25f, 0.25f, 0.25f, 0.05f),
                //extraData = Dust(1f, 1f, 1f, 1f)
        ), finalBlockPos.asVec().add(0.5, 0.5, 0.5))
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
        val formattedTime: String = dateFormat.format(Date(millisTaken))
        val secondsTaken = millisTaken / 1000.0
        val scorePerSecond = if (score < 2) "-.--" else ((score / secondsTaken * 10.0).roundToInt() / 10.0).coerceAtLeast(0.0)

        val message: Component = Component.text()
            .append(Component.text(score, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
            .append(Component.text(" points", NamedTextColor.DARK_PURPLE))
            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
            .append(Component.text(formattedTime, NamedTextColor.GRAY))
            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
            .append(Component.text("${scorePerSecond}bps", NamedTextColor.GRAY))
            .build()

        sendActionBar(message)
    }

    fun playSound(pitch: Float) {
        playSound(
            Sound.sound(
                SoundEvent.BLOCK_NOTE_BLOCK_BASS,
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