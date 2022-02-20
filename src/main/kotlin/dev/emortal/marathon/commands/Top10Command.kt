package dev.emortal.marathon.commands

import dev.emortal.acquaintance.AcquaintanceExtension
import dev.emortal.marathon.MarathonExtension
import dev.emortal.marathon.db.MySQLStorage
import dev.emortal.marathon.game.MarathonGame
import dev.emortal.marathon.utils.armify
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.entity.Player
import world.cepi.kstom.command.kommand.Kommand
import java.util.*

object Top10Command : Kommand({

    defaultSuspending {
        val highscores = MarathonExtension.storage?.getTopHighscoresAsync() ?: return@defaultSuspending

        val message = Component.text()
            .append(Component.text("Marathon Leaderboard\n", NamedTextColor.GOLD, TextDecoration.BOLD))

        var i = 1
        highscores.forEach {
            val playerUsername = AcquaintanceExtension.playerCache[it.key.toString()] ?: "???"

            val formattedTime: String = MarathonGame.DATE_FORMAT.format(Date(it.value.time))

            val color = when (i) {
                1 -> Style.style(NamedTextColor.GOLD, TextDecoration.BOLD)
                2 -> Style.style(TextColor.color(210, 210, 210), TextDecoration.BOLD)
                3 -> Style.style(TextColor.color(205, 127, 50), TextDecoration.BOLD)
                else -> Style.style(TextColor.color(120, 120, 120))
            }
            val nameColor = when (i) {
                1 -> Style.style(NamedTextColor.GOLD)
                2 -> Style.style(TextColor.color(210, 210, 210))
                3 -> Style.style(TextColor.color(205, 127, 50))
                else -> Style.style(TextColor.color(120, 120, 120))
            }
            val scoreColor = when (i) {
                1,2,3 -> Style.style(NamedTextColor.LIGHT_PURPLE)
                else -> Style.style(NamedTextColor.GRAY)
            }

            val bps = (it.value.score.toDouble() / it.value.time) * 1000

            message.append(
                Component.text()
                    .append(Component.text("\n${i}", color))
                    .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(playerUsername, nameColor))
                    .append(Component.space())
                    .append(Component.text(it.value.score, scoreColor))
                    .append(Component.text(" (${formattedTime})", NamedTextColor.DARK_GRAY))
                    .append(Component.space())
                    .append(Component.text("%.2f".format(bps), NamedTextColor.DARK_GRAY))
                    .append(Component.text("bps", NamedTextColor.DARK_GRAY))
            )

            if (i == 3) message.append(Component.newline())
            i++
        }

        if (sender is Player) {
            if (!highscores.keys.contains(player.uuid)) {
                val highscore = MarathonExtension.storage?.getHighscoreAsync(player.uuid)
                val highscorePoints = highscore?.score ?: 0
                val formattedTime: String = MarathonGame.DATE_FORMAT.format(Date(highscore?.time ?: 0))
                val placement = MarathonExtension.storage?.getPlacementAsync(highscorePoints) ?: 0

                val bps = (highscorePoints.toDouble() / (highscore?.time ?: 0)) * 1000

                message.append(
                    Component.text()
                        .append(Component.text(" \n\n${placement}", NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(player.username, NamedTextColor.DARK_AQUA))
                        .append(Component.space())
                        .append(Component.text(highscorePoints, NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.text(" (${formattedTime})", NamedTextColor.DARK_GRAY))
                        .append(Component.text("%.2f".format(bps), NamedTextColor.DARK_GRAY))
                        .append(Component.text("bps", NamedTextColor.DARK_GRAY))
                )
            }
        }


        sender.sendMessage(message.armify())
    }

}, "top10", "leaderboard")