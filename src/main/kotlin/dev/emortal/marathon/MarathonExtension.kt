package dev.emortal.marathon

import dev.emortal.immortal.config.ConfigHelper
import dev.emortal.immortal.config.GameOptions
import dev.emortal.immortal.game.GameManager
import dev.emortal.immortal.game.WhenToRegisterEvents
import dev.emortal.immortal.util.MinestomRunnable
import dev.emortal.marathon.commands.SetScoreCommand
import dev.emortal.marathon.commands.SetTargetCommand
import dev.emortal.marathon.commands.Top10Command
import dev.emortal.marathon.config.DatabaseConfig
import dev.emortal.marathon.db.MySQLStorage
import dev.emortal.marathon.db.Storage
import dev.emortal.marathon.game.MarathonGame
import dev.emortal.marathon.game.MarathonRacingGame
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.extensions.Extension
import world.cepi.kstom.Manager
import java.lang.management.ManagementFactory
import java.lang.management.ThreadMXBean
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


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
        fun threadDump(lockedMonitors: Boolean, lockedSynchronizers: Boolean): String {
            val threadDump = StringBuffer(System.lineSeparator())
            val threadMXBean: ThreadMXBean = ManagementFactory.getThreadMXBean()
            for (threadInfo in threadMXBean.dumpAllThreads(lockedMonitors, lockedSynchronizers)) {
                threadDump.append(threadInfo.toString())
            }
            return threadDump.toString()
        }
        object : MinestomRunnable(repeat = Duration.ofMinutes(20), timer = defaultTimer) {
            override fun run() {
                logger.info("------ NEW DUMP \\/\\/\\/ ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))}")
                logger.info(threadDump(true, true))
            }
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
                showsJoinLeaveMessages = false
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

        logger.info("[${origin.name}] Initialized!")
    }

    override fun terminate() {
        SetTargetCommand.unregister()
        SetScoreCommand.unregister()
        Top10Command.unregister()

        logger.info("[${origin.name}] Terminated!")
    }

}