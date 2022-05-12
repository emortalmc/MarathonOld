package dev.emortal.marathon

import dev.emortal.immortal.config.ConfigHelper
import dev.emortal.immortal.config.GameOptions
import dev.emortal.immortal.game.GameManager
import dev.emortal.immortal.game.WhenToRegisterEvents
import dev.emortal.immortal.util.MinestomRunnable
import dev.emortal.marathon.commands.DiscCommand
import dev.emortal.marathon.commands.SetScoreCommand
import dev.emortal.marathon.commands.SetTargetCommand
import dev.emortal.marathon.commands.Top10Command
import dev.emortal.marathon.config.DatabaseConfig
import dev.emortal.marathon.db.MySQLStorage
import dev.emortal.marathon.db.Storage
import dev.emortal.marathon.game.MarathonGame
import dev.emortal.marathon.game.MarathonRacingGame
import dev.emortal.marathon.gui.MusicPlayerInventory
import kotlinx.coroutines.GlobalScope
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerLoginEvent
import net.minestom.server.extensions.Extension
import net.minestom.server.inventory.Inventory
import world.cepi.kstom.Manager
import world.cepi.kstom.event.listenOnly
import world.cepi.kstom.util.clone
import java.lang.management.ManagementFactory
import java.lang.management.ThreadMXBean
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap


class MarathonExtension : Extension() {
    companion object {
        var databaseConfig = DatabaseConfig()
        val databaseConfigPath = Path.of("./marathon.json")

        var storage: Storage? = null
    }

    override fun initialize() {
        databaseConfig = ConfigHelper.initConfigFile(databaseConfigPath, databaseConfig)

        if (databaseConfig.enabled) {
            storage = MySQLStorage()
        }

        GameManager.registerGame<MarathonGame>(
            "marathon",
            Component.text("Marathon", NamedTextColor.RED, TextDecoration.BOLD),
            showsInSlashPlay = true,
            canSpectate = true,
            WhenToRegisterEvents.GAME_START,
            GameOptions(
                maxPlayers = 1,
                minPlayers = 1,
                countdownSeconds = 0,
                showsJoinLeaveMessages = false,
                allowsSpectators = true
            )
        )

        GameManager.registerGame<MarathonRacingGame>(
            "marathonracing",
            Component.text("Marathon Racing", NamedTextColor.RED, TextDecoration.BOLD),
            showsInSlashPlay = true,
            canSpectate = true,
            WhenToRegisterEvents.GAME_START,
            GameOptions(
                maxPlayers = 8,
                minPlayers = 2,
                //countdownSeconds = 0,
                showsJoinLeaveMessages = true
            )
        )

        SetTargetCommand.register()
        SetScoreCommand.register()
        Top10Command.register()
        DiscCommand.register()
        DiscCommand.refreshSongs()

        logger.info("[${origin.name}] Initialized!")
    }

    override fun terminate() {
        SetTargetCommand.unregister()
        SetScoreCommand.unregister()
        Top10Command.unregister()
        DiscCommand.unregister()

        logger.info("[${origin.name}] Terminated!")
    }

}