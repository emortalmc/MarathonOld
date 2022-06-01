package dev.emortal.marathon.game

import dev.emortal.immortal.config.GameOptions
import dev.emortal.immortal.game.Game
import dev.emortal.immortal.util.armify
import dev.emortal.marathon.MarathonExtension
import dev.emortal.marathon.animation.BlockAnimator
import dev.emortal.marathon.animation.PathAnimator
import dev.emortal.marathon.commands.DiscCommand
import dev.emortal.marathon.db.Highscore
import dev.emortal.marathon.db.MongoStorage
import dev.emortal.marathon.generator.Generator
import dev.emortal.marathon.generator.LegacyGenerator
import dev.emortal.marathon.gui.MusicPlayerInventory
import dev.emortal.marathon.utils.TimeFrame
import dev.emortal.marathon.utils.firsts
import dev.emortal.marathon.utils.sendBlockDamage
import dev.emortal.marathon.utils.updateOrCreateLine
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
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Enchantment
import net.minestom.server.item.ItemHideFlag
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.scoreboard.Sidebar
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import net.minestom.server.utils.NamespaceID
import net.minestom.server.utils.time.TimeUnit
import org.tinylog.kotlin.Logger
import world.cepi.kstom.Manager
import world.cepi.kstom.adventure.noItalic
import world.cepi.kstom.event.listenOnly
import world.cepi.kstom.util.asPos
import world.cepi.kstom.util.asVec
import world.cepi.kstom.util.playSound
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
    private var dailyHighscore: Highscore? = null
    private var weeklyHighscore: Highscore? = null
    private var monthlyHighscore: Highscore? = null

    private var dailyPlacement: Int = 0
    private var weeklyPlacement: Int = 0
    private var monthlyPlacement: Int = 0

    private var passedHighscore = false

    var invalidateRun = false

    var blockPalette = BlockPalette.RAINBOW
        set(value) {
            if (blockPalette == value) return

            playSound(Sound.sound(value.soundEffect, Sound.Source.MASTER, 1f, 1f), Sound.Emitter.self())

            blocks.forEachIndexed { i, block ->
                if (block.second == Block.DIAMOND_BLOCK) return@forEachIndexed
                val newBlock = value.blocks.filter { it != block.second }.random()
                instance.setBlock(block.first, newBlock)
                blocks[i] = Pair(block.first, newBlock)
            }
            instance.entities.filter { it.entityType == EntityType.FALLING_BLOCK }.forEach {
                it.remove()
            }

            field = value
        }

    var blocks = mutableListOf<Pair<Point, Block>>()

    override var spawnPosition = SPAWN_POINT

    init {

    }

    override fun playerJoin(player: Player) = runBlocking {
        highscore = MarathonExtension.mongoStorage?.getHighscore(player.uuid, MongoStorage.leaderboard)
        dailyHighscore = MarathonExtension.mongoStorage?.getHighscore(player.uuid, MongoStorage.daily)
        weeklyHighscore = MarathonExtension.mongoStorage?.getHighscore(player.uuid, MongoStorage.weekly)
        monthlyHighscore = MarathonExtension.mongoStorage?.getHighscore(player.uuid, MongoStorage.monthly)

        val highscorePoints = highscore?.score ?: 0
        val dailyPoints = dailyHighscore?.score ?: 0
        val weeklyPoints = weeklyHighscore?.score ?: 0
        val monthlyPoints = monthlyHighscore?.score ?: 0
        val placement = MarathonExtension.mongoStorage?.getPlacement(highscorePoints, MongoStorage.leaderboard) ?: 0
        dailyPlacement = MarathonExtension.mongoStorage?.getPlacement(dailyPoints, MongoStorage.daily) ?: 11
        weeklyPlacement = MarathonExtension.mongoStorage?.getPlacement(weeklyPoints, MongoStorage.weekly) ?: 11
        monthlyPlacement = MarathonExtension.mongoStorage?.getPlacement(monthlyPoints, MongoStorage.monthly) ?: 11

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
        if (dailyPlacement <= 10 && dailyHighscore != null) {
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
        if (weeklyPlacement <= 10 && weeklyHighscore != null) {
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
        if (monthlyPlacement <= 10 && monthlyHighscore != null) {
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
        listenOnly<PlayerSwapItemEvent> {
            isCancelled = true
        }

        listenOnly<PlayerUseItemEvent> {
            if (this.itemStack.material() == Material.MUSIC_DISC_BLOCKS) {
                this.isCancelled = true
                player.playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 1f, 2f))
                player.openInventory(MusicPlayerInventory.inventory)
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
            val palette = BlockPalette.values()[player.inventory.getItemStack(slot.toInt()).getTag(paletteTag) ?: return@listenOnly]

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

    val spectatorBoatMap = ConcurrentHashMap<UUID, Entity>()

    override fun spectatorJoin(player: Player) {
        player.isAutoViewable = false
        respawnBoat(player, players.first().position)
    }

    override fun spectatorLeave(player: Player) {
        spectatorBoatMap[player.uuid]?.remove()
        spectatorBoatMap.remove(player.uuid)
    }

    private fun reset() = runBlocking {
        val player = players.first()

        if (score == 0) {
            player.teleport(SPAWN_POINT).thenRun {
                player.removeTag(teleportingTag)
            }

            return@runBlocking
        }

        blocks.forEach { block ->
            if (block.first == Block.DIAMOND_BLOCK) return@forEach
            instance.scheduleNextTick {
                instance.setBlock(block.first, Block.AIR)
            }
        }
        blocks.clear()


        val previousScore = score
        score = 0
        combo = 0
        finalBlockPos = Pos(0.0, 149.0, 0.0)

        blocks.add(Pair(finalBlockPos, Block.DIAMOND_BLOCK))
        instance.setBlock(finalBlockPos, Block.DIAMOND_BLOCK)

        animation.tasks.forEach {
            it.cancel()
        }
        animation.tasks.clear()

        instance.entities.filter { it !is Player && it.entityType != EntityType.BOAT }.forEach { it.remove() }

        breakingTask?.cancel()
        breakingTask = null

        passedHighscore = false

        spectators.forEach {
            it.stopSpectating()
            it.teleport(SPAWN_POINT)
            respawnBoat(it, SPAWN_POINT)
        }


        player.exp = 0f

        player.velocity = Vec.ZERO
        player.teleport(SPAWN_POINT).thenRun {
            instance.scheduleNextTick { _ ->
                var i = 0
                lateinit var task: Task
                task = Manager.scheduler.buildTask {
                    i++
                    if (i > length) task.cancel()
                    playSound(Sound.sound(SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP, Sound.Source.MASTER, 1f, 1f + (i.toFloat() / 16f)))
                    generateNextBlock(false)
                }.repeat(TaskSchedule.nextTick()).schedule()

                //generateNextBlock(length, false)
                player.removeTag(teleportingTag)
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

            players.forEach {
                it.exp = (score.toFloat() / (highscore?.score ?: 0).toFloat()).coerceAtMost(1f)
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

        entity.setInstance(instance, newPos).thenRun {
            spectator.spectate(entity)
            instance.scheduleNextTick {
                spectator.spectate(entity)
            }
            spectatorBoatMap[spectator.uuid] = entity
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
            animation.destroyBlockAnimated(blocks[0].first, blocks[0].second)

            blocks.removeAt(0)
        }

        if (finalBlockPos.y() == 150.0) targetY =
            0 else if (finalBlockPos.y() < 135 || finalBlockPos.y() > 240) targetY = 150

        finalBlockPos = generator.getNextPosition(finalBlockPos, targetX, targetY, score)


        //val newPaletteBlock = blockPalette.blocks.filter { it != blocks.last().second }.random()
        val newPaletteBlock = blockPalette.blocks[(blockPalette.blocks.indexOf(blocks.last().second) + 1) % blockPalette.blocks.size]
        val newPaletteBlockPos = finalBlockPos

        val lastBlock = blocks.last()
        if (inGame) animation.setBlockAnimated(newPaletteBlockPos, newPaletteBlock, lastBlock.first)
        else {
            instance.setBlock(newPaletteBlockPos, newPaletteBlock)

            showParticle(
                Particle.particle(
                    type = ParticleType.DUST,
                    count = 1,
                    data = OffsetAndSpeed(0f, 0f, 0f, 0f),
                    extraData = Dust(1f, 0f, 1f, 1.25f)
                ), Vectors(newPaletteBlockPos.asVec().add(0.5, 0.5, 0.5), lastBlock.first.asVec().add(0.5, 0.5, 0.5), 0.35)
            )
        }

        blocks.add(Pair(newPaletteBlockPos, newPaletteBlock))

        instance.showParticle(
            Particle.particle(
                type = ParticleType.DUST,
                count = 1,
                data = OffsetAndSpeed(),//OffsetAndSpeed(0f, 0f, 0f, 0.05f),
                extraData = Dust(1f,0.25f, 1f, 0.9f)
        //), finalBlockPos.asVec().add(0.5, 0.5, 0.5))
        ), Renderer.fixedRectangle(finalBlockPos.asVec().sub(0.1, 0.1, 0.1), finalBlockPos.add(1.1, 1.1, 1.1).asVec(), 0.5))

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
        val scorePerSecond = if (score < 2) "-.-" else ((score / secondsTaken * 10.0).roundToInt() / 10.0).coerceIn(0.0, 9.9)

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

        var minZ = Integer.MAX_VALUE

        blocks.forEach {
            if (it.first.blockY() > maxY) maxY = it.first.blockY()
            if (it.first.blockY() < minY) minY = it.first.blockY()

            if (it.first.blockZ() < minZ) minZ = it.first.blockZ()

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

        // Check global (endless)
        if (score > highscoreScore) {
            val placement = MarathonExtension.mongoStorage?.getPlacement(score, MongoStorage.leaderboard) ?: 0

            playSound(Sound.sound(SoundEvent.ENTITY_PLAYER_LEVELUP, Sound.Source.MASTER, 1f, 0.7f), Sound.Emitter.self())
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
                    .append(Component.text("\n\nYou are now #${placement} on the global leaderboard!", NamedTextColor.GOLD))
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
                TimeFrame.GLOBAL -> return@forEachIndexed
            }

            if (score > timeFrameScore) {
                val placement = MarathonExtension.mongoStorage?.getPlacement(score, it.collection) ?: 11

                val previousPlacement = when (it) {
                    TimeFrame.DAILY -> dailyPlacement
                    TimeFrame.WEEKLY -> weeklyPlacement
                    TimeFrame.MONTHLY -> monthlyPlacement
                    else -> 0
                }

                if (placement <= 10) {
                    if (placement != previousPlacement) {
                        shouldSendMessage = true
                        message.append(Component.text("You are now #${placement} on the ${it.lyName} leaderboard!\n", NamedTextColor.GOLD))

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
        if (DiscCommand.stopPlayingTaskMap.containsKey(players.first())) return

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

        //val schematic: Schematic = SpongeSchematic().also {
        //    it.read(FileInputStream(File("./marathonstarter.schem")))
        //}

        val dimension = Manager.dimensionType.getDimension(NamespaceID.from("fullbright"))!!
        val newInstance = Manager.instance.createInstanceContainer(dimension)
        newInstance.time = 0
        newInstance.timeRate = 0
        newInstance.timeUpdate = null
        newInstance.setBlock(0, 149, 0, Block.DIAMOND_BLOCK)
        //newInstance.chunkLoader = SchematicChunkLoader.builder().addSchematic(schematic).offset(0, 150, 0).build()

        return newInstance
    }

}