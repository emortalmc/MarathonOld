package dev.emortal.marathon

import dev.emortal.immortal.game.GameManager
import dev.emortal.immortal.game.GameOptions
import dev.emortal.immortal.game.WhenToRegisterEvents
import dev.emortal.marathon.db.MariaStorage
import dev.emortal.marathon.game.MarathonGame
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.extensions.Extension
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block

object MarathonExtension : Extension() {

    lateinit var PARKOUR_INSTANCE: Instance
    val storage = MariaStorage()

    override fun initialize() {
        PARKOUR_INSTANCE = MinecraftServer.getInstanceManager().createInstanceContainer()
        //PARKOUR_INSTANCE.timeRate = 0
        PARKOUR_INSTANCE.setBlock(0, 149, 0, Block.DIAMOND_BLOCK)

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
        GameManager.unregisterGame<MarathonGame>()

        logger.info("Terminated!")
    }

}