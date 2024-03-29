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
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import org.litote.kmongo.serialization.SerializationClassMappingTypeService
import org.slf4j.LoggerFactory
import java.nio.file.Path

private val LOGGER = LoggerFactory.getLogger(MarathonMain::class.java)

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

        val cm = MinecraftServer.getCommandManager();
        cm.register(SetScoreCommand)
        cm.register(Top10Command)
    }

    GameManager.registerGame(
        { MarathonGame() },
        "marathon"
    )

    AcquaintanceExtension.init(MinecraftServer.getGlobalEventHandler())

    LOGGER.info("[Marathon] Initialized!")
}

object MarathonMain {
    var databaseConfig = DatabaseConfig()
    val databaseConfigPath = Path.of("./marathon.json")

    var mongoStorage: MongoStorage? = null
}