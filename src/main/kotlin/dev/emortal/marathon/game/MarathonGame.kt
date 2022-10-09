package dev.emortal.marathon.game

import dev.emortal.immortal.config.GameOptions
import dev.emortal.immortal.game.Game
import dev.emortal.immortal.game.GameManager
import dev.emortal.immortal.util.armify
import dev.emortal.marathon.MarathonExtension
import dev.emortal.marathon.animation.BlockAnimator
import dev.emortal.marathon.animation.PathAnimator
import dev.emortal.marathon.db.Highscore
import dev.emortal.marathon.db.MongoStorage
import dev.emortal.marathon.db.PlayerSettings
import dev.emortal.marathon.generator.Generator
import dev.emortal.marathon.generator.LegacyGenerator
import dev.emortal.marathon.utils.TimeFrame
import dev.emortal.marathon.utils.firsts
import dev.emortal.marathon.utils.updateOrCreateLine
import dev.emortal.nbstom.MusicCommand
import dev.emortal.nbstom.MusicPlayerInventory
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
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.player.PlayerChangeHeldSlotEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerSwapItemEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.batch.AbsoluteBlockBatch
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Enchantment
import net.minestom.server.item.ItemHideFlag
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.scoreboard.Sidebar
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.utils.NamespaceID
import net.minestom.server.utils.chunk.ChunkUtils
import net.minestom.server.utils.time.TimeUnit
import org.tinylog.kotlin.Logger
import world.cepi.kstom.Manager
import world.cepi.kstom.adventure.noItalic
import world.cepi.kstom.event.listenOnly
import world.cepi.kstom.util.asPos
import world.cepi.kstom.util.asVec
import world.cepi.kstom.util.chunksInRange
import world.cepi.kstom.util.roundToBlock
import world.cepi.particle.Particle
import world.cepi.particle.ParticleType
import world.cepi.particle.data.OffsetAndSpeed
import world.cepi.particle.extra.Dust
import world.cepi.particle.renderer.Renderer
import world.cepi.particle.showParticle
import world.cepi.particle.util.Vectors
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.collections.set
import kotlin.math.pow
import kotlin.math.roundToInt

class MarathonGame(gameOptions: GameOptions) : Game(gameOptions) {

    companion object {
        val SPAWN_POINT = Pos(0.5, 150.0, 0.5)
        val dateFormat = SimpleDateFormat("mm:ss")
        val accurateDateFormat = SimpleDateFormat("mm:ss.SSS")

        val paletteTag = Tag.Integer("palette")
        val teleportingTag = Tag.Boolean("teleporting")


    }

    val generator: Generator = LegacyGenerator
    val animation: BlockAnimator = PathAnimator(this)

    // Amount of blocks in front of the player
    var length = 6
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
    private var dailyHighscore: Highscore? = null
    private var weeklyHighscore: Highscore? = null
    private var monthlyHighscore: Highscore? = null

    private var dailyPlacement: Int? = 0
    private var weeklyPlacement: Int? = 0
    private var monthlyPlacement: Int? = 0

    private var playerSettings: PlayerSettings? = null

    private var passedHighscore = false

    var invalidateRun = false

    var blockPalette = BlockPalette.RAINBOW
        set(value) {
            if (blockPalette == value) return

            playSound(Sound.sound(value.soundEffect, Sound.Source.MASTER, 1f, 1f), Sound.Emitter.self())

            blocks.forEachIndexed { i, block ->
                if (block.second == Block.DIAMOND_BLOCK) return@forEachIndexed
                val newBlock = value.blocks.filter { it != block.second }.random()
                instance.get()?.setBlock(block.first, newBlock)
                blocks[i] = Pair(block.first, newBlock)
            }
            instance.get()?.entities?.filter { it.entityType == EntityType.FALLING_BLOCK }?.forEach {
                it.remove()
            }

            field = value
        }

    var blocks = CopyOnWriteArrayList<Pair<Point, Block>>()

    val spectatorBoatMap = ConcurrentHashMap<UUID, Entity>()

    override var spawnPosition = SPAWN_POINT

    override fun playerJoin(player: Player) = runBlocking {

        highscore = MarathonExtension.mongoStorage?.getHighscore(player.uuid, MongoStorage.leaderboard)
        dailyHighscore = MarathonExtension.mongoStorage?.getHighscore(player.uuid, MongoStorage.daily)
        weeklyHighscore = MarathonExtension.mongoStorage?.getHighscore(player.uuid, MongoStorage.weekly)
        monthlyHighscore = MarathonExtension.mongoStorage?.getHighscore(player.uuid, MongoStorage.monthly)

        val highscorePoints = highscore?.score ?: 0
//        val dailyPoints = dailyHighscore?.score ?: 0
//        val weeklyPoints = weeklyHighscore?.score ?: 0
//        val monthlyPoints = monthlyHighscore?.score ?: 0
        val placement = MarathonExtension.mongoStorage?.getPlacement(player.uuid, MongoStorage.leaderboard) ?: 0
        dailyPlacement = MarathonExtension.mongoStorage?.getPlacement(player.uuid, MongoStorage.daily)
        weeklyPlacement = MarathonExtension.mongoStorage?.getPlacement(player.uuid, MongoStorage.weekly)
        monthlyPlacement = MarathonExtension.mongoStorage?.getPlacement(player.uuid, MongoStorage.monthly)
        playerSettings = MarathonExtension.mongoStorage?.getSettings(player.uuid)

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
        if (dailyPlacement != null && dailyPlacement!! <= 10 && dailyHighscore != null) {
            needsSpacer = true
            scoreboard?.createLine(
                Sidebar.ScoreboardLine(
                    "dailyPlacementLine",
                    Component.text()
                        .append(Component.text("#${dailyPlacement}", NamedTextColor.GOLD))
                        .append(Component.text(" on daily", NamedTextColor.GRAY))
                        .build(),
                    3
                )
            )
        }
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

        player.setHeldItemSlot(4)

        val instance = instance.get()!!

        when (playerSettings?.theme) {
            "light" -> instance.time = 0
            else -> instance.time = 18000
        }

        //BlockPalette.values().forEachIndexed { i, it ->
        //    val item = ItemStack.builder(it.displayItem)
        //        .displayName(it.displayName.noItalic())
        //        .meta { meta ->
        //            meta.setTag(paletteTag, it.ordinal)
        //            meta
        //        }
        //        .build()
        //
        //    player.inventory.setItemStack(i + 2, item)
        //}
        player.inventory.setItemStack(
            8,
            ItemStack.builder(Material.MUSIC_DISC_BLOCKS)
                .displayName(Component.text("Music", NamedTextColor.GOLD).noItalic())
                .meta { meta ->
                    meta.enchantment(Enchantment.INFINITY, 1)
                    meta.hideFlag(ItemHideFlag.HIDE_ENCHANTS)

                }
                .build()
        )

        player.inventory.setItemStack(
            0,
            ItemStack.builder(Material.CLOCK)
                .displayName(Component.text("Theme Toggle", NamedTextColor.GOLD).noItalic())
                .build()
        )
    }

    override fun playerLeave(player: Player) {
        playerSettings?.let { MarathonExtension.mongoStorage?.saveSettings(player.uuid, it) }
        destroy()
    }

    override fun registerEvents() = with(eventNode) {
        listenOnly<ItemDropEvent> {
            isCancelled = true
        }
        listenOnly<InventoryPreClickEvent> {
            isCancelled = true
        }
        listenOnly<PlayerSwapItemEvent> {
            isCancelled = true
        }

        listenOnly<PlayerUseItemEvent> {
            if (this.itemStack.material() == Material.MUSIC_DISC_BLOCKS) {
                this.isCancelled = true
                player.playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 1f, 2f))
                player.openInventory(MusicPlayerInventory.inventory)
            }

            if (this.itemStack.material() == Material.CLOCK) {
                this.isCancelled = true
                player.playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 1f, 2f))

                playerSettings = when (playerSettings?.theme) {
                    "light" -> {
                        player.sendMessage(Component.text("Set theme is now dark", NamedTextColor.GOLD))
                        instance.time = 18000
                        playerSettings?.copy(theme = "dark")
                    }
                    else -> {
                        player.sendMessage(Component.text("Set theme is now light", NamedTextColor.GOLD))
                        instance.time = 0
                        playerSettings?.copy(theme = "light")
                    }
                }
            }
        }

        listenOnly<PlayerMoveEvent> {
            refreshSpectatorPosition(newPosition.add(0.0, 1.0, 0.0))

            if (!player.hasTag(teleportingTag)) checkPosition(player, newPosition)

            val posUnderPlayer = newPosition.sub(0.0, 1.0, 0.0).roundToBlock().asPos()
            val pos2UnderPlayer = newPosition.sub(0.0, 2.0, 0.0).roundToBlock().asPos()

            val blockPositions = blocks.firsts()

            var index = blockPositions.indexOf(pos2UnderPlayer)

            if (index == -1) index = blockPositions.indexOf(posUnderPlayer)
            if (index == -1 || index == 0) return@listenOnly

            generateNextBlock(index, true)
        }

        listenOnly<PlayerChangeHeldSlotEvent> {
            val palette = BlockPalette.values()[player.inventory.getItemStack(slot.toInt()).getTag(paletteTag)
                ?: return@listenOnly]

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
        player.isAutoViewable = false
        player.teleport(players.first().position).thenRun {
            respawnBoat(player, players.first().position)
        }
    }

    override fun spectatorLeave(player: Player) {
        spectatorBoatMap[player.uuid]?.remove()
        spectatorBoatMap.remove(player.uuid)
    }

    private fun reset() = runBlocking {
        val instance = instance.get()!!
        val player = players.first()

        breakingTask?.cancel()
        animation.flushBreakAnimations()
        breakingTask = null

        if (score == 0) {
            instance.chunksInRange(Pos.ZERO, 3).forEach {
                val chunk = instance.getChunk(it.first, it.second)
                chunk?.sendChunk(player)
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

        val batch = AbsoluteBlockBatch()
        blocks.forEach { block ->
            if (block.second == Block.GRASS_BLOCK) return@forEach
            instance.setBlock(block.first, Block.AIR)
        }

        blocks.clear()
        instance.setBlock(spawnPosition.sub(0.0, 1.0, 0.0), Block.ORANGE_CONCRETE)
        blocks.add(Pair(spawnPosition.sub(0.0, 1.0, 0.0), Block.ORANGE_CONCRETE))

        animation.tasks.forEach {
            it.cancel()
        }
        animation.tasks.clear()

        instance.entities.filter { it !is Player && it.entityType != EntityType.BOAT }.forEach { it.remove() }

        passedHighscore = false

        spectators.forEach {
            it.stopSpectating()
            it.teleport(SPAWN_POINT).thenRun {
                respawnBoat(it, SPAWN_POINT)
            }
        }

        player.velocity = Vec.ZERO

        player.teleport(SPAWN_POINT).thenRun {
            player.removeTag(teleportingTag)

            generateNextBlock(length, false)
        }

        updateActionBar()

        val millisTaken = System.currentTimeMillis() - startTimestamp
        val bps = (previousScore.toDouble() / millisTaken.toDouble()) * 1000.0
        if (bps > 2.75 && previousScore > 30) invalidateRun = true

        if (!invalidateRun) {
            checkHighscores(player, previousScore)
        } else {
            // Player likely cheated, do not record score
            Logger.info("Player ${player.username} had an invalid run. Score: ${previousScore}, bps: ${bps}")
        }

        invalidateRun = false
        startTimestamp = -1
    }

    fun generateNextBlock(times: Int, inGame: Boolean) {
        if (inGame) {

            if (startTimestamp == -1L) {
                actionBarTask = Manager.scheduler.buildTask { updateActionBar() }
                    .repeat(Duration.ofSeconds(1))
                    .schedule()
                startTimestamp = System.currentTimeMillis()
            }

            val powerResult = 2.0.pow(combo / 45.0)
            val maxTimeTaken = 1000L * times / powerResult

            if (System.currentTimeMillis() - lastBlockTimestamp < maxTimeTaken) combo += times else combo = 0

            val basePitch: Float = 0.9f + (combo - 1) * 0.05f

            score += times

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

        var newPos = position.apply { _, y, z, _, _ -> Pos(blocks.sumOf { it.first.x() } / blocks.size, y + 3, z - 6) }
        newPos = newPos.withDirection(position.sub(newPos))

        instance.get()?.let {
            entity.setInstance(it, newPos).thenRun {
                spectator.scheduleNextTick {
                    spectator.spectate(entity)
                }
                spectatorBoatMap[spectator.uuid] = entity
            }
        }
    }

    fun refreshSpectatorPosition(position: Pos) {
        var newPos = position.apply { _, y, z, _, _ -> Pos(blocks.sumOf { it.first.x() } / blocks.size, y + 3, z - 6) }
        newPos = newPos.withDirection(position.sub(newPos))
        spectatorBoatMap.values.filter { it.isActive }.forEach {
            it.teleport(newPos)
        }
        spectators.filter { it.isActive }.forEach {
            it.teleport(newPos)
        }
    }

    fun generateNextBlock(inGame: Boolean = true) {

        if (inGame && blocks.size > length) {
            animation.destroyBlockAnimated(blocks[0].first)

            blocks.removeAt(0)
        }

        if (finalBlockPos.y() == SPAWN_POINT.y) targetY =
            0 else if (finalBlockPos.y() < SPAWN_POINT.blockY() - 17 || finalBlockPos.y() > SPAWN_POINT.blockY() + 30) targetY = SPAWN_POINT.blockY()

        finalBlockPos = generator.getNextPosition(finalBlockPos, targetX, targetY, score)


        //val newPaletteBlock = blockPalette.blocks.filter { it != blocks.last().second }.random()
        val newPaletteBlock =
            blockPalette.blocks[(blockPalette.blocks.indexOf(blocks.last().second) + 1) % blockPalette.blocks.size]
        val newPaletteBlockPos = finalBlockPos

        val lastBlock = blocks.last()
        if (inGame) animation.setBlockAnimated(newPaletteBlockPos, newPaletteBlock, lastBlock.first)
        else {
            instance.get()?.setBlock(newPaletteBlockPos, newPaletteBlock)

            showParticle(
                Particle.particle(
                    type = ParticleType.DUST,
                    count = 1,
                    data = OffsetAndSpeed(0f, 0f, 0f, 0f),
                    extraData = Dust(1f, 0f, 1f, 1.25f)
                ),
                Vectors(newPaletteBlockPos.asVec().add(0.5, 0.5, 0.5), lastBlock.first.asVec().add(0.5, 0.5, 0.5), 0.35)
            )
        }

        blocks.add(Pair(newPaletteBlockPos, newPaletteBlock))

        showParticle(
            Particle.particle(
                type = ParticleType.DUST,
                count = 1,
                data = OffsetAndSpeed(),//OffsetAndSpeed(0f, 0f, 0f, 0.05f),
                extraData = Dust(1f, 0.25f, 1f, 0.9f)
                //), finalBlockPos.asVec().add(0.5, 0.5, 0.5))
            ),
            Renderer.fixedRectangle(
                finalBlockPos.asVec().sub(0.1, 0.1, 0.1),
                finalBlockPos.add(1.1, 1.1, 1.1).asVec(),
                0.5
            )
        )

    }

    private fun createBreakingTask() {
        breakingTask?.cancel()
        breakingTask = MinecraftServer.getSchedulerManager().buildTask {
            animation.destroyBlockAnimated(blocks[0].first, true)
        }.delay(2000, TimeUnit.MILLISECOND).schedule()
    }

    private fun updateActionBar() {
        val millisTaken: Long = if (startTimestamp == -1L) 0 else System.currentTimeMillis() - startTimestamp
        val formattedTime: String = dateFormat.format(Date(millisTaken))
        val secondsTaken = millisTaken / 1000.0
        val scorePerSecond =
            if (score < 2) "-.-" else ((score / secondsTaken * 10.0).roundToInt() / 10.0).coerceIn(0.0, 9.9)

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

    fun checkPosition(player: Player, position: Pos) {

        var maxY = Integer.MIN_VALUE
        var minY = Integer.MAX_VALUE

        var maxX = 0
        var minX = 0

        var maxZ = Integer.MIN_VALUE
        var minZ = Integer.MAX_VALUE

        blocks.forEach {
            if (it.first.blockY() > maxY) maxY = it.first.blockY()
            if (it.first.blockY() < minY) minY = it.first.blockY()

            if (it.first.blockZ() < minZ) minZ = it.first.blockZ()
            if (it.first.blockZ() > maxZ) maxZ = it.first.blockZ()

            if (it.first.blockX() > maxX) maxX = it.first.blockX()
            if (it.first.blockX() < minX) minX = it.first.blockX()
        }


        // Check for player too far up
        if (!invalidateRun && (maxY + 3.5) < position.y) {
            invalidateRun = true
        }

        // Check for player too far down
        if ((minY - 3.0) > position.y) {
            player.setTag(teleportingTag, true)
            reset()
        }

        // Check for player too far behind
        if (minZ - 5 > position.z) {
            player.setTag(teleportingTag, true)
            //player.teleport(SPAWN_POINT)
            reset()
        }

        if (maxX + 6 < position.x) {
            player.setTag(teleportingTag, true)
            reset()
        }
        if (minX - 6 > position.x) {
            player.setTag(teleportingTag, true)
            reset()
        }

    }

    suspend fun checkHighscores(player: Player, score: Int) {

        val highscoreScore = highscore?.score ?: 0

        val millisTaken = System.currentTimeMillis() - startTimestamp
        val newHighscoreObject = Highscore(player.uuid.toString(), score, millisTaken, System.currentTimeMillis())

        // Check lifetime
        if (score > highscoreScore) {
            val placement = MarathonExtension.mongoStorage?.getPlacement(player.uuid, MongoStorage.leaderboard) ?: 0

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

            highscore = newHighscoreObject
            MarathonExtension.mongoStorage?.setHighscore(newHighscoreObject, MongoStorage.leaderboard)
        }

        val dailyScore = dailyHighscore?.score ?: 0
        val weeklyScore = weeklyHighscore?.score ?: 0
        val monthlyScore = monthlyHighscore?.score ?: 0

        val message = Component.text().append(Component.newline())
        var shouldSendMessage = false

        TimeFrame.values().forEachIndexed { i, it ->
            val timeFrameScore = when (it) {
                TimeFrame.DAILY -> dailyScore
                TimeFrame.WEEKLY -> weeklyScore
                TimeFrame.MONTHLY -> monthlyScore
                TimeFrame.LIFETIME -> return@forEachIndexed
            }

            if (score > timeFrameScore) {
                val placement = MarathonExtension.mongoStorage?.getPlacementByScore(timeFrameScore, it.collection) ?: 11

                val previousPlacement = when (it) {
                    TimeFrame.DAILY -> dailyPlacement
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
                        TimeFrame.DAILY -> {
                            dailyHighscore = newHighscoreObject
                            dailyPlacement = placement
                        }
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
                    MarathonExtension.mongoStorage?.setHighscore(newHighscoreObject, it.collection)
                }
            }

        }

        if (shouldSendMessage) player.sendMessage(message.armify())
    }

    fun playSound(pitch: Float) {
        //if (MusicCommand.stopPlayingTaskMap.containsKey(players.first())) return

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

        val dimension = Manager.dimensionType.getDimension(NamespaceID.from("fullbright"))!!
        val newInstance = Manager.instance.createInstanceContainer(dimension)
        newInstance.time = 0
        newInstance.timeRate = 0
        newInstance.timeUpdate = null
        //newInstance.setBlock(0, SPAWN_POINT.blockY() - 1, 0, Block.ORANGE_CONCRETE)

        newInstance.setTag(GameManager.doNotUnloadChunksIndex, ChunkUtils.getChunkIndex(0, 0))
        newInstance.setTag(GameManager.doNotUnloadChunksRadius, 3)

        //val tntSource = FileTNTSource(Path.of("./starter.tnt"))
        //newInstance.chunkLoader = TNTLoader(tntSource, Vec(0.0, (SPAWN_POINT.blockY() - 65).toDouble(), 0.0))
        //newInstance.chunkLoader = AnvilLoader("./starter.tnt")

        return newInstance
    }

}