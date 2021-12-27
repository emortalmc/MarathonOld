package dev.emortal.marathon

import dev.emortal.immortal.config.ConfigHelper
import dev.emortal.immortal.game.GameManager
import dev.emortal.immortal.game.GameOptions
import dev.emortal.immortal.game.WhenToRegisterEvents
import dev.emortal.marathon.config.DatabaseConfig
import dev.emortal.marathon.game.MarathonGame
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.extensions.Extension
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.NamespaceID
import world.cepi.kstom.Manager
import java.nio.file.Path
import java.util.*

class MarathonExtension : Extension() {
    companion object {
        lateinit var parkourInstance: Instance

        var databaseConfig = DatabaseConfig()
        val databaseConfigPath = Path.of("./marathon.json")
    }

    override fun initialize() {
        databaseConfig = ConfigHelper.initConfigFile(databaseConfigPath, databaseConfig)

        val dimension = Manager.dimensionType.getDimension(NamespaceID.from("fullbright"))!!

        parkourInstance = InstanceContainer(UUID.randomUUID(), dimension)
        parkourInstance.time = 0
        parkourInstance.timeRate = 0
        parkourInstance.setBlock(0, 149, 0, Block.DIAMOND_BLOCK)

        GameManager.registerGame<MarathonGame>(
            eventNode,
            "marathon",
            Component.text("Marathon", NamedTextColor.RED, TextDecoration.BOLD),
            true,
            WhenToRegisterEvents.GAME_START,
            GameOptions(
                maxPlayers = 1,
                minPlayers = 1,
                countdownSeconds = 0,
                showsJoinLeaveMessages = false
            )
        )

        logger.info("Initialized!")
    }

    override fun terminate() {
        logger.info("Terminated!")
    }

}