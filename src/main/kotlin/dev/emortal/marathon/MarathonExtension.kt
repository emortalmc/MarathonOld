package dev.emortal.marathon

import dev.emortal.immortal.config.ConfigHelper
import dev.emortal.immortal.config.GameOptions
import dev.emortal.immortal.game.GameManager
import dev.emortal.immortal.game.WhenToRegisterEvents
import dev.emortal.marathon.commands.SetScoreCommand
import dev.emortal.marathon.commands.Top10Command
import dev.emortal.marathon.config.DatabaseConfig
import dev.emortal.marathon.db.MongoStorage
import dev.emortal.marathon.game.MarathonGame
import dev.emortal.nbstom.NBS
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.extensions.Extension
import org.litote.kmongo.serialization.SerializationClassMappingTypeService
import java.nio.file.Path


class MarathonExtension : Extension() {
    companion object {
        var databaseConfig = DatabaseConfig()
        val databaseConfigPath = Path.of("./marathon.json")

        var mongoStorage: MongoStorage? = null
    }

    override fun initialize() {
        databaseConfig = ConfigHelper.initConfigFile(databaseConfigPath, databaseConfig)

        if (databaseConfig.enabled) {
            // Required for some reason, idk - literally says to use it for minecraft plugins
            System.setProperty("org.litote.mongo.mapping.service", SerializationClassMappingTypeService::class.qualifiedName!!)

            mongoStorage = MongoStorage()
            mongoStorage?.init()
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

        SetScoreCommand.register()
        Top10Command.register()
        NBS.registerCommands()

        logger.info("[${origin.name}] Initialized!")
    }

    override fun terminate() {
        SetScoreCommand.unregister()
        Top10Command.unregister()

        MongoStorage.client?.close()

        logger.info("[${origin.name}] Terminated!")
    }

}