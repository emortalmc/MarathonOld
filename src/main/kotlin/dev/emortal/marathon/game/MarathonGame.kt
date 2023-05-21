package dev.emortal.marathon.game

import dev.emortal.immortal.game.Game
import dev.emortal.immortal.game.GameManager
import dev.emortal.immortal.util.*
import dev.emortal.marathon.MarathonMain
import dev.emortal.marathon.animation.BlockAnimator
import dev.emortal.marathon.animation.NoAnimator
import dev.emortal.marathon.db.Highscore
import dev.emortal.marathon.db.MongoStorage
import dev.emortal.marathon.db.PlayerSettings
import dev.emortal.marathon.generator.Generator
import dev.emortal.marathon.generator.LegacyGenerator
import dev.emortal.marathon.utils.TimeFrame
import dev.emortal.marathon.utils.sendBlockDamage
import dev.emortal.marathon.utils.updateOrCreateLine
import dev.emortal.marathon.animation.PathAnimator
import dev.emortal.marathon.animation.SuvatAnimator
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.player.PlayerChangeHeldSlotEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerSwapItemEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.event.trait.InstanceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.scoreboard.Sidebar
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.utils.NamespaceID
import net.minestom.server.utils.time.TimeUnit
import org.slf4j.LoggerFactory
import world.cepi.particle.Particle
import world.cepi.particle.ParticleType
import world.cepi.particle.data.OffsetAndSpeed
import world.cepi.particle.extra.Dust
import world.cepi.particle.showParticle
import world.cepi.particle.util.Vectors
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayDeque
import kotlin.collections.set
import kotlin.math.pow
import kotlin.math.roundToInt

private val LOGGER = LoggerFactory.getLogger(MarathonGame::class.java)

class MarathonGame : Game() {

    companion object {
        val SPAWN_POINT = Pos(0.5, 150.0, 0.5)
        val dateFormat = SimpleDateFormat("mm:ss")
        val accurateDateFormat = SimpleDateFormat("mm:ss.SSS")

        val paletteTag = Tag.Integer("palette")
        val teleportingTag = Tag.Boolean("teleporting")

        val toggleSoundItem = ItemStack.builder(Material.NOTE_BLOCK)
            .displayName(Component.text("Toggle Sound", NamedTextColor.GOLD).noItalic())
            .build()
        val noDistractionsItem = ItemStack.builder(Material.ENDER_EYE)
            .displayName(Component.text("No Distractions", NamedTextColor.GOLD).noItalic())
            .lore(
                Component.text("- Hide score popup").noItalic(),
                Component.text("- Hide scoreboard").noItalic(),
                Component.text("- Disable animations & particles").noItalic(),
                Component.text("- Reduce volume of sounds").noItalic()
            )
            .build()
        val themeToggleItem = ItemStack.builder(Material.CLOCK)
            .displayName(Component.text("Theme Toggle", NamedTextColor.GOLD).noItalic())
            .build()
        val speedrunToggleItem = ItemStack.builder(Material.LEATHER_BOOTS)
            .displayName(Component.text("Toggle Speedrun", NamedTextColor.GOLD).noItalic())
            .build()
    }

    override val maxPlayers: Int = 1
    override val minPlayers: Int = 1
    override val countdownSeconds: Int = 0
    override val canJoinDuringGame: Boolean = false
    override val showScoreboard: Boolean = MarathonMain.mongoStorage != null
    override val showsJoinLeaveMessages: Boolean = false
    override val allowsSpectators: Boolean = true
    override val gameName = "marathon"
    override val gameComponent = Component.text("Marathon", NamedTextColor.RED, TextDecoration.BOLD)

    val generator: Generator = LegacyGenerator
    var animation: BlockAnimator = PathAnimator()

    var length = 8 // Amount of blocks in front of the player
    var targetY = SPAWN_POINT.blockY()
    var targetX = 0

    var actionBarTask: Task? = null
    var breakingTask: Task? = null
    var currentBreakingProgress = 0

    var finalBlockPos: Point = Pos(0.0, SPAWN_POINT.y - 1.0, 0.0)

    var lastBlockTimestamp = 0L
    var startTimestamp = -1L

    var score = -1
    var combo = 0
    var target = -1

    private var highscore: Highscore? = null
    private var weeklyHighscore: Highscore? = null
    private var monthlyHighscore: Highscore? = null

    private var weeklyPlacement: Int? = 0
    private var monthlyPlacement: Int? = 0

    private lateinit var playerSettings: PlayerSettings

    private var passedHighscore = false

    private var invalidateRun = false

    var blockPalette = BlockPalette.OVERWORLD
        set(value) {
            if (blockPalette == value) return

            playSound(Sound.sound(value.soundEffect, Sound.Source.MASTER, 1f, 1f), Sound.Emitter.self())
            instance!!.entities.filter { it.entityType == EntityType.FALLING_BLOCK }.forEach {
                it.remove()
            }

            blocks.forEach {
                instance!!.setBlock(it, value.blocks.random())
            }

            field = value
        }

    val blocks = ArrayDeque<Point>(length)

    private val spectatorBoatMap = ConcurrentHashMap<UUID, Entity>()

    override fun getSpawnPosition(player: Player, spectator: Boolean): Pos =
        if (spectator) players.first().position else SPAWN_POINT

    override fun playerJoin(player: Player) = runBlocking {

        playerSettings = PlayerSettings(player.uuid.toString())

        if (MarathonMain.mongoStorage != null) {
            highscore = MarathonMain.mongoStorage?.getHighscore(player.uuid, MongoStorage.leaderboard)
            weeklyHighscore = MarathonMain.mongoStorage?.getHighscore(player.uuid, MongoStorage.weekly)
            monthlyHighscore = MarathonMain.mongoStorage?.getHighscore(player.uuid, MongoStorage.monthly)

            val highscorePoints = highscore?.score ?: 0
            val placement = MarathonMain.mongoStorage?.getPlacement(player.uuid, MongoStorage.leaderboard) ?: 0
            weeklyPlacement = MarathonMain.mongoStorage?.getPlacement(player.uuid, MongoStorage.weekly)
            monthlyPlacement = MarathonMain.mongoStorage?.getPlacement(player.uuid, MongoStorage.monthly)

            playerSettings = MarathonMain.mongoStorage?.getSettings(player.uuid) ?: PlayerSettings(player.uuid.toString())

            scoreboard?.createLine(
                Sidebar.ScoreboardLine(
                    "highscoreLine",
                    Component.text()
                        .append(Component.text("Highscore: ", NamedTextColor.GRAY))
                        .append(Component.text(highscorePoints, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                        .build(),
                    1
                )
            )
            scoreboard?.createLine(
                Sidebar.ScoreboardLine(
                    "placementLine",
                    Component.text()
                        .append(Component.text("#${placement}", NamedTextColor.LIGHT_PURPLE))
                        .append(Component.text(" on leaderboard", NamedTextColor.GRAY))
                        .build(),
                    0
                )
            )

            var needsSpacer = false
            if (weeklyPlacement != null && weeklyPlacement!! <= 10 && weeklyHighscore != null) {
                needsSpacer = true
                scoreboard?.createLine(
                    Sidebar.ScoreboardLine(
                        "weeklyPlacementLine",
                        Component.text()
                            .append(Component.text("#${weeklyPlacement}", NamedTextColor.GOLD))
                            .append(Component.text(" on weekly", NamedTextColor.GRAY))
                            .build(),
                        4
                    )
                )
            }
            if (monthlyPlacement != null && monthlyPlacement!! <= 10 && monthlyHighscore != null) {
                needsSpacer = true
                scoreboard?.createLine(
                    Sidebar.ScoreboardLine(
                        "monthlyPlacementLine",
                        Component.text()
                            .append(Component.text("#${monthlyPlacement}", NamedTextColor.GOLD))
                            .append(Component.text(" on monthly", NamedTextColor.GRAY))
                            .build(),
                        5
                    )
                )
            }

            if (needsSpacer) scoreboard?.createLine(
                Sidebar.ScoreboardLine(
                    "placementSpacer",
                    Component.empty(),
                    2
                )
            )
        } else {
            playerSettings = PlayerSettings(player.uuid.toString())
        }

        player.setHeldItemSlot(1)

        when (playerSettings.theme) {
            "light" -> instance!!.time = 0
            "dark" -> instance!!.time = 18000
        }

        BlockPalette.values().forEachIndexed { i, it ->
            val item = ItemStack.builder(it.displayItem)
                .displayName(it.displayName.noItalic())
                .meta { meta ->
                    meta.setTag(paletteTag, it.ordinal)
                }
                .build()

            player.inventory.setItemStack(i + 2, item)
        }


        player.inventory.setItemStack(9, speedrunToggleItem)
        player.inventory.setItemStack(10, themeToggleItem)
        player.inventory.setItemStack(11, noDistractionsItem)
        player.inventory.setItemStack(12, toggleSoundItem)
    }

    override fun playerLeave(player: Player) {
//        playerSettings?.let { MarathonExtension.mongoStorage?.saveSettings(player.uuid, it) }
        end()
    }

    override fun registerEvents(eventNode: EventNode<InstanceEvent>) {
        eventNode.addListener(ItemDropEvent::class.java) { e ->
            e.isCancelled = true
        }
        eventNode.addListener(InventoryPreClickEvent::class.java) { e -> runBlocking {
            e.isCancelled = true

            when (e.clickedItem.material()) {
                themeToggleItem.material() -> {
                    e.player.playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 1f, 2f))

                    playerSettings = when (playerSettings.theme) {
                        "light" -> {
                            e.player.sendMessage(Component.text("Set theme is now dark", NamedTextColor.GOLD))
                            e.instance.time = 18000
                            playerSettings.copy(theme = "dark")
                        }
                        else -> {
                            e.player.sendMessage(Component.text("Set theme is now light", NamedTextColor.GOLD))
                            e.instance.time = 0
                            playerSettings.copy(theme = "light")
                        }
                    }
                    MarathonMain.mongoStorage?.saveSettings(playerSettings)
                }

                speedrunToggleItem.material() -> {
                    playerSettings = if (playerSettings.speedrunMode) {
                        e.player.sendMessage(Component.text("Disabled speedrun mode", NamedTextColor.GOLD))
                        target = -1
                        playerSettings.copy(speedrunMode = false)
                    } else {
                        e.player.sendMessage(Component.text("Enabled speedrun mode - Your target is 100", NamedTextColor.GOLD))
                        target = 100
                        playerSettings.copy(speedrunMode = true)
                    }
                    MarathonMain.mongoStorage?.saveSettings(playerSettings)
                }

                noDistractionsItem.material() -> {
                    playerSettings = if (playerSettings.noDistractions) {
                        e.player.sendMessage(Component.text("Disabled no distractions", NamedTextColor.GOLD))

                        scoreboard?.addViewer(e.player)
                        animation = PathAnimator()
                        e.player.level = 0

                        playerSettings.copy(noDistractions = false)
                    } else {
                        e.player.sendMessage(Component.text("Enabled no distractions", NamedTextColor.GOLD))

                        scoreboard?.removeViewer(e.player)
                        animation = NoAnimator
                        e.player.sendActionBar(Component.empty())
                        e.player.level = score
                        playerSettings.copy(noDistractions = true)
                    }
                    MarathonMain.mongoStorage?.saveSettings(playerSettings)
                }

                toggleSoundItem.material() -> {
                    playerSettings = if (playerSettings.noSounds) {
                        e.player.sendMessage(Component.text("Enabled sounds", NamedTextColor.GOLD))
                        playerSettings.copy(noSounds = false)
                    } else {
                        e.player.sendMessage(Component.text("Disabled sounds", NamedTextColor.GOLD))
                        playerSettings.copy(noSounds = true)
                    }
                }

                else -> {}
            }


        }}
        eventNode.addListener(PlayerSwapItemEvent::class.java) { e ->
            e.isCancelled = true
        }

        eventNode.addListener(PlayerUseItemEvent::class.java) { e ->
            e.isCancelled = true
        }

        eventNode.addListener(PlayerMoveEvent::class.java) { e ->
            if (e.player.hasTag(GameManager.playerSpectatingTag)) return@addListener

            val newPosition = e.newPosition
            refreshSpectatorPosition(newPosition.add(0.0, 1.0, 0.0))

            synchronized(blocks) {
                if (!e.player.hasTag(teleportingTag)) checkPosition(e.player, newPosition)

                val posUnderPlayer = Pos.fromPoint(newPosition.sub(0.0, 1.0, 0.0).roundToBlock())
                val pos2UnderPlayer = Pos.fromPoint(newPosition.sub(0.0, 2.0, 0.0).roundToBlock())


                var index = blocks.indexOf(pos2UnderPlayer)

                if (index == -1) index = blocks.indexOf(posUnderPlayer)
                if (index == -1 || index == 0) return@addListener

                generateNextBlock(index, true)
            }
        }

        eventNode.addListener(PlayerChangeHeldSlotEvent::class.java) { e ->
            val palette = BlockPalette.values()[e.player.inventory.getItemStack(e.slot.toInt()).getTag(paletteTag)
                ?: return@addListener]

            if (blockPalette == palette) return@addListener

            blockPalette = palette
        }
    }

    override fun gameStarted() {
        startTimestamp = System.currentTimeMillis()
        scoreboard?.removeLine("infoLine")
        reset(players.first())
    }

    override fun gameEnded() {
        breakingTask?.cancel()
        actionBarTask?.cancel()
        breakingTask = null
        actionBarTask = null

        highscore = null
        weeklyHighscore = null
        monthlyHighscore = null

        blocks.clear()
        spectatorBoatMap.clear()
    }

    override fun spectatorJoin(player: Player) {
        player.isAutoViewable = false
        player.teleport(players.first().position).thenRun {
            respawnBoat(player, players.first().position)
        }
    }

    override fun spectatorLeave(player: Player) {
        spectatorBoatMap[player.uuid]?.remove()
        spectatorBoatMap.remove(player.uuid)
    }

    private fun reset(player: Player) = runBlocking {
        breakingTask?.cancel()
        breakingTask = null

        animation.onReset(this@MarathonGame)

        if (score == 0) {
            for (cx in -1..1) {
                for (cz in -1..1) {
                    val chunk = instance!!.getChunk(cx, cz)
                    chunk?.sendChunk(player)
                }
            }

            player.teleport(SPAWN_POINT).thenRun {
                player.removeTag(teleportingTag)
            }

            return@runBlocking
        }

        val previousScore = score
        score = 0
        combo = 0
        finalBlockPos = Pos(0.0, SPAWN_POINT.y - 1.0, 0.0)

        synchronized(blocks) {
            blocks.forEach { block ->
                instance!!.setBlock(block, Block.AIR)
                instance!!.setBlock(block.add(0.0, 1.0, 0.0), Block.AIR)
            }

            blocks.clear()
            blocks.add(SPAWN_POINT.sub(0.0, 1.0, 0.0))
        }

        instance!!.setBlock(SPAWN_POINT.sub(0.0, 1.0, 0.0), Block.ORANGE_CONCRETE)
        instance!!.entities.filter { it !is Player && it.entityType != EntityType.BOAT }.forEach { it.remove() }

        passedHighscore = false

        spectators.forEach {
            it.stopSpectating()
            it.teleport(SPAWN_POINT).thenRun {
                respawnBoat(it, SPAWN_POINT.add(0.0, 1.0, 0.0))
            }
        }

        player.velocity = Vec.ZERO

        player.teleport(SPAWN_POINT).thenRun {
            player.removeTag(teleportingTag)

            generateNextBlock(length, false)

            val radius = 3
            for (x in -radius..radius) {
                for (z in -radius..radius) {
                    instance?.loadChunk(x, z)?.thenAccept { it.sendChunk() }
                }
            }
        }

        updateActionBar()

        val millisTaken = System.currentTimeMillis() - startTimestamp
        val bps = (previousScore.toDouble() / millisTaken.toDouble()) * 1000.0
        if (bps > 2.75 && previousScore > 30) invalidateRun = true

        if (!invalidateRun) {
            checkHighscores(player, previousScore)
        } else {
            // Player likely cheated, do not record score
            LOGGER.info("Player ${player.username} had an invalid run. Score: ${previousScore}, bps: ${bps}")
        }

        invalidateRun = false
        startTimestamp = -1
    }

    fun generateNextBlock(times: Int, inGame: Boolean) {
        if (inGame) {

            if (startTimestamp == -1L) {
                actionBarTask = instance!!.scheduler().buildTask { updateActionBar() }
                    .repeat(Duration.ofSeconds(1))
                    .schedule()
                startTimestamp = System.currentTimeMillis()
            }

            val powerResult = 2.0.pow(combo / 45.0)
            val maxTimeTaken = 1000L * times / powerResult

            if (System.currentTimeMillis() - lastBlockTimestamp < maxTimeTaken) combo += times else combo = 0

            val basePitch: Float = 0.9f + (combo - 1) * 0.05f

            score += times

            if (target != -1 && score >= target) {
                val millisTaken = System.currentTimeMillis() - startTimestamp
                val timeFormatted = accurateDateFormat.format(Date(millisTaken))

                reset(players.first())

                sendMessage(
                    Component.text()
                        .append(Component.text("You reached your target of ", NamedTextColor.GRAY))
                        .append(Component.text(target, NamedTextColor.GOLD))
                        .append(Component.text(" in ", NamedTextColor.GRAY))
                        .append(Component.text(timeFormatted, NamedTextColor.GOLD))
                )

                return
            }

            if (MarathonMain.mongoStorage != null) {
                val highscorePoints = highscore?.score ?: 0
                if (!passedHighscore && score > highscorePoints) {
                    if (!playerSettings.noDistractions) {
                        sendMessage(
                            Component.text()
                                .append(Component.text("\nYou beat your previous highscore of ", NamedTextColor.GRAY))
                                .append(Component.text(highscorePoints, NamedTextColor.GREEN))
                                .append(Component.text("\nSee how much further you can go!", NamedTextColor.GOLD))
                                .append(Component.text("\n"))
                                .armify()
                        )
                        playSound(Sound.sound(SoundEvent.ENTITY_VILLAGER_CELEBRATE, Sound.Source.MASTER, 1f, 1f))
                    }

                    passedHighscore = true
                }
            }

            if (!playerSettings.noDistractions) showTitle(
                Title.title(
                    Component.empty(),
                    Component.text(score, TextColor.fromHexString("#ff00a6")!!, TextDecoration.BOLD),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ofMillis(100))
                )
            )

            playSound(basePitch)
            createBreakingTask()
            updateActionBar()
        }
        repeat(times) { generateNextBlock(inGame) }
        lastBlockTimestamp = System.currentTimeMillis()
    }

    fun respawnBoat(spectator: Player, position: Pos) {
        spectatorBoatMap[spectator.uuid]?.remove()
        val entity = Entity(EntityType.BOAT)
        entity.isInvisible = true
        entity.setNoGravity(true)
        entity.updateViewableRule { it == spectator }

        synchronized(blocks) {
            var newPos = position.apply { _, y, z, _, _ -> Pos(blocks.sumOf { it.x() } / blocks.size, y + 3, z - 6) }
            newPos = newPos.withDirection(position.sub(newPos))

            entity.setInstance(instance!!, newPos).thenRun {
                spectator.scheduleNextTick {
                    spectator.spectate(entity)
                }
                spectatorBoatMap[spectator.uuid] = entity
            }
        }
    }

    fun refreshSpectatorPosition(position: Pos) {
        synchronized(blocks) {
            var newPos = position.apply { _, y, z, _, _ -> Pos(blocks.sumOf { it.x() } / blocks.size, y + 3, z - 6) }
            newPos = newPos.withDirection(position.sub(newPos))
            spectatorBoatMap.values.filter { it.isActive }.forEach {
                it.teleport(newPos)
            }
            spectators.forEach {
                it.teleport(newPos)
            }
        }
    }

    fun generateNextBlock(inGame: Boolean = true) {
        synchronized(blocks) {
            if (inGame && blocks.size > length) {
                animation.destroyBlockAnimated(this, blocks.first(), Block.AIR)
                animation.destroyBlockAnimated(this, blocks.first().add(0.0, 1.0, 0.0), Block.AIR)

                blocks.removeFirst()
            }

            if (finalBlockPos.y() == SPAWN_POINT.y) targetY =
                0 else if (finalBlockPos.y() < SPAWN_POINT.blockY() - 17 || finalBlockPos.y() > SPAWN_POINT.blockY() + 30) targetY = SPAWN_POINT.blockY()

            finalBlockPos = generator.getNextPosition(finalBlockPos, targetX, targetY, score)


            val newPaletteBlock = blockPalette.blocks.random()

            val newPaletteBlockPos = finalBlockPos

            val lastBlock = blocks.last()
            if (inGame) animation.setBlockAnimated(this, newPaletteBlockPos, newPaletteBlock, lastBlock)
            else {
                instance!!.setBlock(newPaletteBlockPos, newPaletteBlock)

                showParticle(
                    Particle.particle(
                        type = ParticleType.DUST,
                        count = 1,
                        data = OffsetAndSpeed(0f, 0f, 0f, 0f),
                        extraData = Dust(1f, 0f, 1f, 1.25f)
                    ),
                    Vectors(Vec.fromPoint(newPaletteBlockPos.add(0.5, 0.5, 0.5)), Vec.fromPoint(lastBlock.add(0.5, 0.5, 0.5)), 0.35)
                )
            }

            blocks.add(newPaletteBlockPos)

//        showParticle(
//            Particle.particle(
//                type = ParticleType.DUST,
//                count = 1,
//                data = OffsetAndSpeed(),//OffsetAndSpeed(0f, 0f, 0f, 0.05f),
//                extraData = Dust(1f, 0.25f, 1f, 0.9f)
//                //), finalBlockPos.asVec().add(0.5, 0.5, 0.5))
//            ),
//            Renderer.fixedRectangle(
//                finalBlockPos.asVec().sub(0.1, 0.1, 0.1),
//                finalBlockPos.add(1.1, 1.1, 1.1).asVec(),
//                0.5
//            )
//        )
        }
    }

    private fun createBreakingTask() {
        synchronized(blocks) {
            currentBreakingProgress = 0

            breakingTask?.cancel()
            breakingTask = instance!!.scheduler().buildTask {
                if (blocks.size < 1) return@buildTask

                val block = blocks[0]

                currentBreakingProgress += 2

                if (currentBreakingProgress > 8) {
                    generateNextBlock()
                    createBreakingTask()

                    return@buildTask
                }

                playSound(Sound.sound(SoundEvent.BLOCK_WOOD_HIT, Sound.Source.BLOCK, 0.5f, 1f), block)
                sendBlockDamage(block, currentBreakingProgress.toByte())
            }.delay(2000, TimeUnit.MILLISECOND).repeat(500, TimeUnit.MILLISECOND).schedule()
        }
    }

    private fun updateActionBar() {
        if (playerSettings.noDistractions) {
            players.forEach {
                it.level = score
            }
        } else {
            val millisTaken: Long = if (startTimestamp == -1L) 0 else System.currentTimeMillis() - startTimestamp
            val formattedTime: String = dateFormat.format(Date(millisTaken))
            val secondsTaken = millisTaken / 1000.0
            val scorePerSecond =
                if (score < 2) "-.-" else ((score / secondsTaken * 10.0).roundToInt() / 10.0).coerceIn(0.0, 9.9)

            val message: Component = Component.text()
                .append(Component.text(score, TextColor.fromHexString("#ff00a6")!!, TextDecoration.BOLD))
                .append(Component.text(" points", TextColor.fromHexString("#bf007c")!!))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(formattedTime, NamedTextColor.GRAY))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("${scorePerSecond}bps", NamedTextColor.GRAY))
                .build()

            sendActionBar(message)
        }
    }

    fun checkPosition(player: Player, position: Pos) {

        var maxY = Integer.MIN_VALUE
        var minY = Integer.MAX_VALUE

        var maxX = 0
        var minX = 0

        var maxZ = Integer.MIN_VALUE
        var minZ = Integer.MAX_VALUE

        blocks.forEach {
            if (it.blockY() > maxY) maxY = it.blockY()
            if (it.blockY() < minY) minY = it.blockY()

            if (it.blockZ() < minZ) minZ = it.blockZ()
            if (it.blockZ() > maxZ) maxZ = it.blockZ()

            if (it.blockX() > maxX) maxX = it.blockX()
            if (it.blockX() < minX) minX = it.blockX()
        }


        // Check for player too far up
        if (!invalidateRun && (maxY + 3.5) < position.y) {
            invalidateRun = true
        }

        // Check for player too far down
        if ((minY - 3.0) > position.y) {
            player.setTag(teleportingTag, true)
            reset(player)
        }

        // Check for player too far behind
        if (minZ - 5 > position.z) {
            player.setTag(teleportingTag, true)
            //player.teleport(SPAWN_POINT)
            reset(player)
        }

        if (maxX + 6 < position.x) {
            player.setTag(teleportingTag, true)
            reset(player)
        }
        if (minX - 6 > position.x) {
            player.setTag(teleportingTag, true)
            reset(player)
        }

    }

    suspend fun checkHighscores(player: Player, score: Int) {
        if (MarathonMain.mongoStorage == null) {
            return
        }

        val highscoreScore = highscore?.score ?: 0

        val millisTaken = System.currentTimeMillis() - startTimestamp
        val newHighscoreObject = Highscore(player.uuid.toString(), score, millisTaken, System.currentTimeMillis())

        // Check lifetime
        if (score > highscoreScore) {
            highscore = newHighscoreObject
            MarathonMain.mongoStorage?.setHighscore(newHighscoreObject, MongoStorage.leaderboard)
            val placement = MarathonMain.mongoStorage?.getPlacement(player.uuid, MongoStorage.leaderboard) ?: 0

            playSound(
                Sound.sound(SoundEvent.ENTITY_PLAYER_LEVELUP, Sound.Source.MASTER, 1f, 0.7f),
                Sound.Emitter.self()
            )
            showTitle(
                Title.title(
                    Component.empty(),
                    Component.text("You beat your previous highscore!", NamedTextColor.GOLD),
                    Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(1500), Duration.ofMillis(1000))
                )
            )

            sendMessage(
                Component.text()
                    .append(Component.text("\nYou beat your previous highscore of ", NamedTextColor.GRAY))
                    .append(Component.text(highscoreScore, NamedTextColor.GREEN))
                    .append(Component.text("\nYour new highscore is ", NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text(score, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                    .append(
                        Component.text(
                            "\n\nYou are now #${placement} on the lifetime leaderboard!",
                            NamedTextColor.GOLD
                        )
                    )
                    .append(Component.text("\n"))
                    .armify()
            )

            scoreboard?.updateLineContent(
                "highscoreLine",
                Component.text()
                    .append(Component.text("Highscore: ", NamedTextColor.GRAY))
                    .append(Component.text(score, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                    .build()
            )
            scoreboard?.updateLineContent(
                "placementLine",
                Component.text()
                    .append(Component.text("#${placement}", NamedTextColor.GOLD))
                    .append(Component.text(" on leaderboard", NamedTextColor.GRAY))
                    .build()
            )
        }

        val weeklyScore = weeklyHighscore?.score ?: 0
        val monthlyScore = monthlyHighscore?.score ?: 0

        val message = Component.text().append(Component.newline())
        var shouldSendMessage = false

        TimeFrame.values().forEachIndexed { i, it ->
            val timeFrameScore = when (it) {
                TimeFrame.WEEKLY -> weeklyScore
                TimeFrame.MONTHLY -> monthlyScore
                TimeFrame.LIFETIME -> return@forEachIndexed
            }

            if (score > timeFrameScore) {
                MarathonMain.mongoStorage?.setHighscore(newHighscoreObject, it.collection)

                val placement = MarathonMain.mongoStorage?.getPlacement(player.uuid, it.collection) ?: 11

                val previousPlacement = when (it) {
                    TimeFrame.WEEKLY -> weeklyPlacement
                    TimeFrame.MONTHLY -> monthlyPlacement
                    else -> 0
                }

                if (placement <= 10) {
                    if (placement != previousPlacement) {
                        shouldSendMessage = true
                        message.append(
                            Component.text(
                                "You are now #${placement} on the ${it.lyName} leaderboard!\n",
                                NamedTextColor.GOLD
                            )
                        )

                        scoreboard?.updateOrCreateLine(
                            "${it.lyName}PlacementLine",
                            Component.text()
                                .append(Component.text("#${placement}", NamedTextColor.GOLD))
                                .append(Component.text(" on ${it.lyName}", NamedTextColor.GRAY))
                                .build(),
                            i + 2
                        )
                    }

                    when (it) {
                        TimeFrame.WEEKLY -> {
                            weeklyHighscore = newHighscoreObject
                            weeklyPlacement = placement
                        }
                        TimeFrame.MONTHLY -> {
                            monthlyHighscore = newHighscoreObject
                            monthlyPlacement = placement
                        }
                        else -> {}
                    }
                }
            }

        }

        if (shouldSendMessage) player.sendMessage(message.armify())
    }

    fun playSound(pitch: Float) {
        //if (MusicCommand.stopPlayingTaskMap.containsKey(players.first())) return

        if (!playerSettings.noSounds) playSound(
            Sound.sound(
                SoundEvent.BLOCK_NOTE_BLOCK_BASS,
                Sound.Source.MASTER,
                if (playerSettings.noDistractions) 0.7f else 2f,
                pitch
            ),
            Sound.Emitter.self()
        )
    }

    // Marathon is not winnable
    override fun victory(winningPlayers: Collection<Player>) {
    }

    override fun instanceCreate(): CompletableFuture<Instance> {

        val dimension = MinecraftServer.getDimensionTypeManager().getDimension(NamespaceID.from("fullbright"))!!
        val newInstance = MinecraftServer.getInstanceManager().createInstanceContainer(dimension)
        newInstance.time = 0
        newInstance.timeRate = 0
        newInstance.timeUpdate = null
        //newInstance.setBlock(0, SPAWN_POINT.blockY() - 1, 0, Block.ORANGE_CONCRETE)

//        newInstance.setTag(GameManager.doNotUnloadChunksIndex, ChunkUtils.getChunkIndex(0, 0))
//        newInstance.setTag(GameManager.doNotUnloadChunksRadius, 3)

        //val tntSource = FileTNTSource(Path.of("./starter.tnt"))
        //newInstance.chunkLoader = TNTLoader(tntSource, Vec(0.0, (SPAWN_POINT.blockY() - 65).toDouble(), 0.0))
        //newInstance.chunkLoader = AnvilLoader("./starter.tnt")

        return CompletableFuture.completedFuture(newInstance)
    }

}