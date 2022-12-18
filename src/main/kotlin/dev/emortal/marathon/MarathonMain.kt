package dev.emortal.marathon

import dev.emortal.acquaintance.AcquaintanceExtension
import dev.emortal.immortal.Immortal
import dev.emortal.immortal.config.ConfigHelper
import dev.emortal.immortal.game.GameManager
import dev.emortal.marathon.commands.SetScoreCommand
import dev.emortal.marathon.commands.Top10Command
import dev.emortal.marathon.config.DatabaseConfig
import dev.emortal.marathon.db.MongoStorage
import dev.emortal.marathon.game.MarathonGame
import dev.emortal.marathon.game.MarathonRacingGame
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.extensions.Extension
import org.litote.kmongo.serialization.SerializationClassMappingTypeService
import org.tinylog.kotlin.Logger
import world.cepi.kstom.command.register
import world.cepi.kstom.command.unregister
import java.nio.file.Path


fun main() {
    Immortal.initAsServer()

    MarathonMain.databaseConfig = ConfigHelper.initConfigFile(
        MarathonMain.databaseConfigPath,
        MarathonMain.databaseConfig
    )

    if (MarathonMain.databaseConfig.enabled) {
        System.setProperty("org.litote.mongo.mapping.service", SerializationClassMappingTypeService::class.qualifiedName!!)

        MarathonMain.mongoStorage = MongoStorage()
        MarathonMain.mongoStorage!!.init()

        SetScoreCommand.register()
        Top10Command.register()
    }

    GameManager.registerGame<MarathonGame>(
        "marathon",
        Component.text("Marathon", NamedTextColor.RED, TextDecoration.BOLD),
        showsInSlashPlay = true
    )

    GameManager.registerGame<MarathonRacingGame>(
        "marathonracing",
        Component.text("Marathon Racing", NamedTextColor.RED, TextDecoration.BOLD),
        showsInSlashPlay = true
    )

    AcquaintanceExtension.init(MinecraftServer.getGlobalEventHandler())

    Logger.info("[Marathon] Initialized!")
}

object MarathonMain {
    var databaseConfig = DatabaseConfig()
    val databaseConfigPath = Path.of("./marathon.json")

    var mongoStorage: MongoStorage? = null
}