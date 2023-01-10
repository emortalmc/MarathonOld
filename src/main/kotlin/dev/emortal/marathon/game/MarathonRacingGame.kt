package dev.emortal.marathon.game

import dev.emortal.immortal.game.Game
import dev.emortal.immortal.util.asPos
import dev.emortal.immortal.util.asVec
import dev.emortal.immortal.util.playSound
import dev.emortal.immortal.util.roundToBlock
import dev.emortal.marathon.animation.BlockAnimator
import dev.emortal.marathon.animation.FallingSandAnimator
import dev.emortal.marathon.generator.Generator
import dev.emortal.marathon.generator.RacingGenerator
import dev.emortal.marathon.utils.sendBlockDamage
import kotlinx.coroutines.NonCancellable.cancel
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.minecart.MinecartMeta
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.trait.InstanceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.scoreboard.Sidebar
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import net.minestom.server.utils.NamespaceID
import world.cepi.particle.Particle
import world.cepi.particle.ParticleType
import world.cepi.particle.data.OffsetAndSpeed
import world.cepi.particle.showParticle
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier
import kotlin.math.pow

class MarathonRacingGame : Game() {

    override val maxPlayers: Int = 8
    override val minPlayers: Int = 2
    override val countdownSeconds: Int = 20
    override val canJoinDuringGame: Boolean = false
    override val showScoreboard: Boolean = true
    override val showsJoinLeaveMessages: Boolean = true
    override val allowsSpectators: Boolean = true

    companion object {
        val SPAWN_POINT = Pos(0.5, 150.0, 0.5)
        private val dateFormat = SimpleDateFormat("mm:ss")
    }

    val generator: Generator = RacingGenerator
    val animation: BlockAnimator = FallingSandAnimator

    var targetY = 150
    var targetX = 0

    // Amount of blocks in front of the player
    var length = 8

    var lastBlockTimestamp = 0L
    var startTimestamp = -1L

    var blockPalette = BlockPalette.RAINBOW

    var firstPlace: ParkourRacer? = null

    val racerMap = ConcurrentHashMap<Player, ParkourRacer>()

    private var timerTask: Task? = null
    private var endGameTask: Task? = null

    override fun getSpawnPosition(player: Player, spectator: Boolean): Pos = SPAWN_POINT

    override fun playerJoin(player: Player) {
        player.gameMode = GameMode.SPECTATOR
        player.isFlying = true
        player.isAllowFlying = true

        scoreboard?.createLine(Sidebar.ScoreboardLine(
            "${player.username}ScoreLine",
            Component.text()
                .append(Component.text(player.username, NamedTextColor.GRAY))
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(0, NamedTextColor.LIGHT_PURPLE))
                .build(),
            0
        ))
    }

    override fun playerLeave(player: Player) {
        racerMap.remove(player)
        scoreboard?.removeLine("${player.username}ScoreLine")
    }

    override fun registerEvents(eventNode: EventNode<InstanceEvent>) {

        eventNode.addListener(PlayerMoveEvent::class.java) { e ->
            val racer = racerMap[e.player]
            val blocks = racer?.blocks ?: return@addListener

            val newPosition = e.newPosition

            if (newPosition.y() < (blocks.minOf { it.y() }) - 3) {
                e.player.teleport(racer.playerPos)
                reset(e.player, true)
                return@addListener
            }

            val posUnderPlayer = newPosition.sub(0.0, 1.0, 0.0).roundToBlock().asPos()
            val pos2UnderPlayer = newPosition.sub(0.0, 2.0, 0.0).roundToBlock().asPos()

            var index = blocks.indexOf(pos2UnderPlayer)

            if (index == -1) index = blocks.indexOf(posUnderPlayer)
            if (index == -1 || index == 0) return@addListener

            generateNextBlock(e.player, index, true)
        }
    }

    override fun gameStarted() {
        startTimestamp = System.currentTimeMillis()
        scoreboard?.removeLine("infoLine")

        val gameLength = 3 * 60 * 1000L // 3 minutes as milliseconds

        scoreboard?.updateLineScore("headerSpacer", 9999)

        scoreboard?.createLine(Sidebar.ScoreboardLine(
            "timerLine",
            Component.empty(),
            9998
        ))

        timerTask = instance!!.scheduler().buildTask {
            val timeTaken = System.currentTimeMillis() - startTimestamp
            val timeLeft = gameLength - timeTaken

            scoreboard?.updateLineContent(
                "timerLine",
                Component.text()
                    .append(Component.text("Time left: ", NamedTextColor.GRAY))
                    .append(Component.text(dateFormat.format(Date(timeLeft)), NamedTextColor.DARK_GRAY))
                    .build()
            )
        }.repeat(Duration.ofSeconds(1)).schedule()

        endGameTask = instance!!.scheduler().buildTask {
            if (firstPlace == null) {
                end()
                return@buildTask
            }

            val firstPlacePlayer = racerMap.maxBy { it.value.highscore }.key

            endGameTask?.cancel()
            timerTask?.cancel()

            victory(firstPlacePlayer)
        }.delay(Duration.ofMillis(gameLength)).schedule()

        players.forEachIndexed { i,it ->
            it.gameMode = GameMode.ADVENTURE
            it.isFlying = false
            it.isAllowFlying = false

            racerMap[it] = ParkourRacer(Pos(i * 15.0, 150.0, 0.0))

            reset(it, false)
        }
    }

    override fun gameEnded() {
        timerTask?.cancel()
        endGameTask?.cancel()
        racerMap.clear()
    }

    private fun reset(player: Player, inGame: Boolean) = runBlocking {
        val racer = racerMap[player] ?: return@runBlocking

        if (racer.score == 0) return@runBlocking

        synchronized(racer.blocks) {
            racer.blocks.forEach {
                instance!!.setBlock(it, Block.AIR)
            }
            racer.blocks.clear()

            val diamondBlockPos = racer.spawnPos.sub(0.0, 1.0, 0.0)
            instance!!.setBlock(diamondBlockPos, Block.DIAMOND_BLOCK)
            racer.blocks.add(diamondBlockPos)

            generateNextBlock(player, length, false)
        }
        racer.breakingTask?.cancel()
        racer.score = 0
        racer.combo = 0

        if (inGame) {
            sendMessage(
                Component.text()
                    .append(Component.text("☠", NamedTextColor.RED))
                    .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(player.username, NamedTextColor.RED))
                    .append(Component.text(" fell!", NamedTextColor.GRAY))
            )
            //checkFirstPlace()

            /*scoreboard?.updateLineContent(
                "${player.username}ScoreLine",
                Component.text()
                    .append(Component.text(player.username, NamedTextColor.GRAY))
                    .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(0, NamedTextColor.LIGHT_PURPLE))
                    .build()
            )
            scoreboard?.updateLineScore(
                "${player.username}ScoreLine",
                0
            )*/
        } else {
            player.teleport(racer.playerPos)
        }

        player.velocity = Vec.ZERO

    }

    fun generateNextBlock(player: Player, times: Int, inGame: Boolean) {
        val racer = racerMap[player] ?: return

        if (inGame) {
            createBreakingTask(player, racer)

            val powerResult = 2.0.pow(racer.combo / 30.0)
            val maxTimeTaken = 1000L * times / powerResult

            if (System.currentTimeMillis() - lastBlockTimestamp < maxTimeTaken) racer.combo += times else racer.combo = 0

            val basePitch: Float = 1 + (racer.combo - 1) * 0.05f

            racer.score += times

            checkFirstPlace()

            scoreboard?.updateLineContent(
                "${player.username}ScoreLine",
                Component.text()
                    .append(Component.text(player.username, NamedTextColor.GRAY))
                    .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(racer.highscore, NamedTextColor.LIGHT_PURPLE))
                    .build()
            )
            scoreboard?.updateLineScore(
                "${player.username}ScoreLine",
                racer.highscore
            )

            player.showTitle(
                Title.title(
                    Component.empty(),
                    Component.text(racer.score, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(200), Duration.ofMillis(200))
                )
            )

            playSound(player, basePitch)
        }
        repeat(times) { generateNextBlock(player) }
        lastBlockTimestamp = System.currentTimeMillis()
    }

    fun generateNextBlock(player: Player) {
        val racer = racerMap[player] ?: return

        if (racer.blocks.size > length) {
            animation.destroyBlockAnimated(this, racer.blocks.removeFirst(), Block.ACACIA_WOOD)
        }

        val finalBlockPos = racer.blocks.last()

        if (finalBlockPos.y() == 150.0) targetY =
            0 else if (finalBlockPos.y() < 135 || finalBlockPos.y() > 240) targetY = 150

        val newPos = generator.getNextPosition(finalBlockPos, racer.spawnPos.x.toInt(), targetY, racer.score)
        racer.blocks.add(newPos)

        val newPaletteBlock = blockPalette.blocks.random()

        animation.setBlockAnimated(this, newPos, newPaletteBlock, finalBlockPos)

        showParticle(
            Particle.particle(
                type = ParticleType.CLOUD,
                count = 10,
                data = OffsetAndSpeed(0.25f, 0.25f, 0.25f, 0.05f),
                //extraData = Dust(1f, 1f, 1f, 1f)
        ), finalBlockPos.asVec().add(0.5, 0.5, 0.5))
    }

    fun playSound(player: Player, pitch: Float) {
        player.playSound(
            Sound.sound(
                SoundEvent.BLOCK_NOTE_BLOCK_BASS,
                Sound.Source.MASTER,
                3f,
                pitch
            ),
            Sound.Emitter.self()
        )
    }

    private fun createBreakingTask(player: Player, racer: ParkourRacer) {
        racer.breakingTask?.cancel()

        racer.breakingTask = player.scheduler().submitTask(object : Supplier<TaskSchedule> {
            var currentBreakingProgress = 0
            var first = true

            override fun get(): TaskSchedule {
                if (first) {
                    first = false
                    return TaskSchedule.seconds(1)
                }

                if (currentBreakingProgress > 8) {
                    createBreakingTask(player, racer)
                    generateNextBlock(player)
                    return TaskSchedule.stop()
                }

                currentBreakingProgress++

                val block = synchronized(racer.blocks) {
                    racer.blocks[0]
                }

                player.playSound(Sound.sound(SoundEvent.BLOCK_WOOD_HIT, Sound.Source.BLOCK, 0.5f, 1f), block)
                sendBlockDamage(block, currentBreakingProgress.toByte())

                return TaskSchedule.millis(500)
            }
        })
    }

    fun checkFirstPlace() {
        val highestScore = racerMap.maxByOrNull { it.value.highscore }
        if (highestScore != null && firstPlace != highestScore.value && highestScore.value.score > (firstPlace?.score ?: 0)) {
            firstPlace = highestScore.value

            sendMessage(
                Component.text()
                    .append(Component.text("★", NamedTextColor.YELLOW))
                    .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(highestScore.key.username, NamedTextColor.YELLOW))
                    .append(Component.text(" is now in first place!", NamedTextColor.GRAY))
            )

            playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 1f, 1f), Sound.Emitter.self())
        }
    }

    override fun instanceCreate(): CompletableFuture<Instance> {
        val dimension = MinecraftServer.getDimensionTypeManager().getDimension(NamespaceID.from("fullbright"))!!
        val newInstance = MinecraftServer.getInstanceManager().createInstanceContainer(dimension)
        newInstance.time = 0
        newInstance.timeRate = 0
        newInstance.setBlock(0, 149, 0, Block.DIAMOND_BLOCK)

        return CompletableFuture.completedFuture(newInstance)
    }

}