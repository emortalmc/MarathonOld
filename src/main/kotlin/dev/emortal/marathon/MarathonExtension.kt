package dev.emortal.marathon

import dev.emortal.marathon.game.MarathonGame
import emortal.immortal.game.GameManager
import emortal.immortal.game.GameOptions
import emortal.immortal.game.GameTypeInfo
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.extensions.Extension
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import world.cepi.kstom.event.listenOnly

object MarathonExtension : Extension() {

    lateinit var PARKOUR_INSTANCE: Instance

    override fun initialize() {
        PARKOUR_INSTANCE = MinecraftServer.getInstanceManager().createInstanceContainer()
        PARKOUR_INSTANCE.time = -1 // Negative number disables daylight cycle
        PARKOUR_INSTANCE.timeRate = 0
        PARKOUR_INSTANCE.setBlock(0, 149, 0, Block.DIAMOND_BLOCK)

        eventNode.listenOnly<PlayerSpawnEvent> {
            player.sendMessage("spawned in instance ${spawnInstance.uniqueId}")
        }

        GameManager.registerGame<MarathonGame>(
            GameTypeInfo(
                eventNode,
                "marathon",
                Component.text("Marathon", NamedTextColor.RED, TextDecoration.BOLD),
                true,
                GameOptions(
                    { PARKOUR_INSTANCE },
                    1,
                    1,
                    false,
                    false,
                    true,
                    false
                )
            )
        )

        logger.info("Initialized!")
    }

    override fun terminate() {
        logger.info("Teminated!")
    }

}