package dev.emortal.marathon.commands

import dev.emortal.acquaintance.AcquaintanceExtension
import dev.emortal.marathon.MarathonExtension
import dev.emortal.marathon.db.MySQLStorage
import dev.emortal.marathon.game.MarathonGame
import dev.emortal.marathon.utils.armify
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import world.cepi.kstom.command.kommand.Kommand
import java.util.*

object Top10Command : Kommand({

    defaultSuspending {
        val highscores = MarathonExtension.storage!!.getTopHighscoresAsync() ?: return@defaultSuspending

        val message = Component.text()
            .append(Component.text("Marathon Leaderboard\n", NamedTextColor.GOLD, TextDecoration.BOLD))

        highscores.forEach {
            val playerUsername = AcquaintanceExtension.playerCache[it.key.toString()] ?: "???"

            val formattedTime: String = MarathonGame.DATE_FORMAT.format(Date(it.value.time))

            message.append(
                Component.text()
                    .append(Component.text("\n - ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(playerUsername, NamedTextColor.GRAY))
                    .append(Component.space())
                    .append(Component.text(it.value.score, NamedTextColor.GOLD))
                    .append(Component.text(" (${formattedTime})", NamedTextColor.DARK_GRAY))
            )
        }

        sender.sendMessage(message.armify())
    }

}, "top10", "leaderboard")