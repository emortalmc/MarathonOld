package dev.emortal.marathon.commands

import dev.emortal.acquaintance.RelationshipManager.getCachedUsername
import dev.emortal.immortal.util.centerText
import dev.emortal.immortal.util.parsed
import dev.emortal.marathon.MarathonExtension
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
            .append(Component.text(centerText("Marathon Leaderboard", bold = true), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
            .append(Component.newline())

        var i = 1
        highscores.forEach {
            val playerUsername = it.key.getCachedUsername() ?: "???"

            //val formattedTime: String = MarathonGame.dateFormat.format(Date(it.value.time))

            val color = when (i) {
                1 -> Style.style(NamedTextColor.GOLD, TextDecoration.BOLD)
                2 -> Style.style(TextColor.color(210, 210, 210), TextDecoration.BOLD)
                3 -> Style.style(TextColor.color(205, 127, 50), TextDecoration.BOLD)
                else -> Style.style(TextColor.color(140, 140, 140))
            }
            val nameColor = when (i) {
                1 -> Style.style(NamedTextColor.GOLD)
                2 -> Style.style(TextColor.color(210, 210, 210))
                3 -> Style.style(TextColor.color(205, 127, 50))
                else -> Style.style(TextColor.color(140, 140, 140))
            }
            val scoreColor = when (i) {
                1,2,3 -> Style.style(NamedTextColor.LIGHT_PURPLE)
                else -> Style.style(NamedTextColor.YELLOW)
            }
            val timesColor = when (i) {
                1,2,3 -> Style.style(NamedTextColor.GRAY)
                else -> Style.style(TextColor.color(110, 110, 110))
            }

            val bps = (it.value.score.toDouble() / it.value.time) * 1000

            message.append(
                Component.text()
                    .append(Component.text("\n #", color).decoration(TextDecoration.BOLD, false))
                    .append(Component.text(i, color))
                    .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(playerUsername, nameColor))
                    .append(Component.space())
                    .append(Component.text(it.value.score, scoreColor))
                    .append(Component.text(" (${(it.value.time / 1000).parsed()}", timesColor))
                    .append(Component.text("%.2f".format(bps), timesColor))
                    .append(Component.text("bps)", timesColor))
            )

            if (i == 3) message.append(Component.newline())
            i++
        }

        if (sender is Player) {
            if (!highscores.keys.contains(player.uuid)) {
                val highscore = MarathonExtension.storage?.getHighscoreAsync(player.uuid)
                val highscorePoints = highscore?.score ?: 0
                val formattedTime: String = MarathonGame.dateFormat.format(Date(highscore?.time ?: 0))
                val placement = MarathonExtension.storage?.getPlacementAsync(highscorePoints) ?: 0

                val bps = (highscorePoints.toDouble() / (highscore?.time ?: 0)) * 1000

                message.append(
                    Component.text()
                        .append(Component.text(" \n\n${placement}", NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(player.username, NamedTextColor.DARK_AQUA))
                        .append(Component.space())
                        .append(Component.text(highscorePoints, NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.text(" (${formattedTime}) ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("%.2f".format(bps), NamedTextColor.DARK_GRAY))
                        .append(Component.text("bps", NamedTextColor.DARK_GRAY))
                )
            }
        }


        sender.sendMessage(message.armify())
    }

}, "top10", "leaderboard", "lb")