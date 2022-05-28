package dev.emortal.marathon

import dev.emortal.immortal.config.ConfigHelper
import dev.emortal.immortal.config.GameOptions
import dev.emortal.immortal.game.GameManager
import dev.emortal.immortal.game.WhenToRegisterEvents
import dev.emortal.marathon.commands.DiscCommand
import dev.emortal.marathon.commands.SetScoreCommand
import dev.emortal.marathon.commands.SetTargetCommand
import dev.emortal.marathon.commands.Top10Command
import dev.emortal.marathon.config.DatabaseConfig
import dev.emortal.marathon.db.MySQLStorage
import dev.emortal.marathon.db.Storage
import dev.emortal.marathon.game.MarathonGame
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.extensions.Extension
import java.nio.file.Path


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

        /*GameManager.registerGame<MarathonRacingGame>(
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
        )*/

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