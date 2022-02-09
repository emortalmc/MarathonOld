package dev.emortal.marathon.game

import dev.emortal.immortal.game.Game
import dev.emortal.immortal.game.GameOptions
import dev.emortal.marathon.animation.BlockAnimator
import dev.emortal.marathon.animation.FallingSandAnimator
import dev.emortal.marathon.generator.Generator
import dev.emortal.marathon.generator.RacingGenerator
import dev.emortal.marathon.utils.*
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.scoreboard.Sidebar
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.Task
import net.minestom.server.utils.NamespaceID
import world.cepi.kstom.Manager
import world.cepi.kstom.event.listenOnly
import world.cepi.kstom.util.asPos
import world.cepi.kstom.util.asVec
import world.cepi.kstom.util.roundToBlock
import world.cepi.particle.Particle
import world.cepi.particle.ParticleType
import world.cepi.particle.data.OffsetAndSpeed
import world.cepi.particle.showParticle
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import kotlin.math.pow

class MarathonRacingGame(gameOptions: GameOptions) : Game(gameOptions) {

    companion object {
        val SPAWN_POINT = Pos(0.5, 150.0, 0.5)
        val DATE_FORMAT = SimpleDateFormat("mm:ss")
    }

    val generator: Generator = RacingGenerator
    val animation: BlockAnimator = FallingSandAnimator(this)

    var targetY = 150
    var targetX = 0

    // Amount of blocks in front of the player
    var length = 8

    var lastBlockTimestamp = 0L
    var startTimestamp = -1L

    var blockPalette = BlockPalette.RAINBOW

    var firstPlace: ParkourRacer? = null

    val racerMap = mutableMapOf<Player, ParkourRacer>()

    private var timerTask: Task? = null
    private var endGameTask: Task? = null

    override var spawnPosition = SPAWN_POINT

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
        destroy()
    }

    override fun registerEvents() {

        eventNode.listenOnly<PlayerMoveEvent> {
            val blocks = racerMap[player]?.blocks ?: return@listenOnly

            if (newPosition.y() < (blocks.minOf { it.y() }) - 3) {
                reset(player, true)
                return@listenOnly
            }

            val posUnderPlayer = newPosition.sub(0.0, 1.0, 0.0).roundToBlock().asPos()
            val pos2UnderPlayer = newPosition.sub(0.0, 2.0, 0.0).roundToBlock().asPos()

            var index = blocks.indexOf(pos2UnderPlayer)

            if (index == -1) index = blocks.indexOf(posUnderPlayer)
            if (index == -1 || index == 0) return@listenOnly

            generateNextBlock(player, index, true)
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

        timerTask = Manager.scheduler.buildTask {
            val timeTaken = System.currentTimeMillis() - startTimestamp
            val timeLeft = gameLength - timeTaken

            scoreboard?.updateLineContent(
                "timerLine",
                Component.text()
                    .append(Component.text("Time left: ", NamedTextColor.GRAY))
                    .append(Component.text(DATE_FORMAT.format(Date(timeLeft)), NamedTextColor.DARK_GRAY))
                    .build()
            )
        }.repeat(Duration.ofSeconds(1)).schedule()

        endGameTask = Manager.scheduler.buildTask {
            if (firstPlace == null) {
                destroy()
                return@buildTask
            }

            endGameTask?.cancel()
            timerTask?.cancel()

            victory(firstPlace!!.player)
        }.delay(Duration.ofMillis(gameLength)).schedule()

        players.forEachIndexed { i,it ->
            it.gameMode = GameMode.ADVENTURE
            it.isFlying = false
            it.isAllowFlying = false

            racerMap[it] = ParkourRacer(it, Pos(i * 15.0, 150.0, 0.0))

            reset(it, false)
        }
    }

    override fun gameDestroyed() {
        timerTask?.cancel()
        endGameTask?.cancel()
        racerMap.clear()
    }

    private fun reset(player: Player, inGame: Boolean) = runBlocking {
        val racer = racerMap[player] ?: return@runBlocking

        racer.blocks.forEach {
            instance.setBlock(it, Block.AIR)
        }
        racer.blocks.clear()

        racer.score = 0
        racer.combo = 0

        if (inGame) {
            sendMessage(Component.text("${player.username} fell!", NamedTextColor.YELLOW))
            checkFirstPlace()

            scoreboard?.updateLineContent(
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
            )
        }

        val diamondBlockPos = racer.spawnPos.sub(0.0, 1.0, 0.0)
        racer.blocks.add(diamondBlockPos)

        player.velocity = Vec.ZERO
        player.teleport(racer.spawnPos)

        instance.setBlock(diamondBlockPos, Block.DIAMOND_BLOCK)

        generateNextBlock(player, length, false)

    }

    fun generateNextBlock(player: Player, times: Int, inGame: Boolean) {
        val racer = racerMap[player] ?: return

        if (inGame) {
            if (startTimestamp == -1L) {
                startTimestamp = System.currentTimeMillis()
            }

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
                    .append(Component.text(racer.score, NamedTextColor.LIGHT_PURPLE))
                    .build()
            )
            scoreboard?.updateLineScore(
                "${player.username}ScoreLine",
                racer.score
            )

            player.showTitle(
                Title.title(
                    Component.empty(),
                    Component.text(racer.score, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                    Title.Times.of(Duration.ZERO, Duration.ofMillis(200), Duration.ofMillis(200))
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
            animation.destroyBlockAnimated(racer.blocks.removeAt(0))
        }

        val finalBlockPos = racer.blocks.last()

        if (finalBlockPos.y() == 150.0) targetY =
            0 else if (finalBlockPos.y() < 135 || finalBlockPos.y() > 240) targetY = 150

        val newPos = generator.getNextPosition(finalBlockPos, racer.spawnPos.x.toInt(), targetY, racer.score)
        racer.blocks.add(newPos)

        //val newPaletteBlock = blockPalette.blocks.random()

        animation.setBlockAnimated(newPos, Block.STONE, finalBlockPos)

        instance.showParticle(
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

    fun checkFirstPlace() {
        val highestScore = racerMap.values.maxByOrNull { it.score }
        if (highestScore != null && firstPlace != highestScore) {
            firstPlace = highestScore

            sendMessage(Component.text("${highestScore.player.username} is now in first place!", NamedTextColor.YELLOW))
            playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 1f, 1f), Sound.Emitter.self())
        }
    }

    override fun instanceCreate(): Instance {
        val dimension = Manager.dimensionType.getDimension(NamespaceID.from("fullbright"))!!
        val newInstance = Manager.instance.createInstanceContainer(dimension)
        newInstance.time = 0
        newInstance.timeRate = 0
        newInstance.setBlock(0, 149, 0, Block.DIAMOND_BLOCK)

        return newInstance
    }

}