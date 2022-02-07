package dev.emortal.marathon

import dev.emortal.immortal.config.ConfigHelper
import dev.emortal.immortal.game.GameManager
import dev.emortal.immortal.game.GameOptions
import dev.emortal.immortal.game.WhenToRegisterEvents
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
import java.util.*

class MarathonExtension : Extension() {
    companion object {
        //lateinit var parkourInstance: InstanceContainer

        var databaseConfig = DatabaseConfig()
        val databaseConfigPath = Path.of("./marathon.json")

        var storage: Storage? = null
    }

    override fun initialize() {
        databaseConfig = ConfigHelper.initConfigFile(databaseConfigPath, databaseConfig)

        if (databaseConfig.enabled) {
            storage = MySQLStorage()

            //runBlocking {
            //    println((storage as MySQLStorage).getTopHighscoresAsync())
            //}
        }

        //val dimension = Manager.dimensionType.getDimension(NamespaceID.from("fullbright"))!!

        // Using InstanceContainer so instance isn't automatically registered on startup,
        // instance gets manually registered when at least one game of Marathon is created


        GameManager.registerGame<MarathonGame>(
            eventNode,
            "marathon",
            Component.text("Marathon", NamedTextColor.RED, TextDecoration.BOLD),
            showsInSlashPlay = true,
            canSpectate = true,
            WhenToRegisterEvents.GAME_START,
            GameOptions(
                maxPlayers = 1,
                minPlayers = 1,
                countdownSeconds = 0,
                showsJoinLeaveMessages = false
            )
        )

        Top10Command.register()

        logger.info("Initialized!")
    }

    override fun terminate() {
        Top10Command.unregister()

        logger.info("Terminated!")
    }

}