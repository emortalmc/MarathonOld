package dev.emortal.marathon.commands

import dev.emortal.acquaintance.RelationshipManager.getCachedUsername
import dev.emortal.immortal.util.armify
import dev.emortal.immortal.util.centerText
import dev.emortal.immortal.util.parsed
import dev.emortal.marathon.MarathonExtension
import dev.emortal.marathon.commands.Top10Command.runCommand
import dev.emortal.marathon.utils.TimeFrame
import dev.emortal.marathon.utils.enumValueOrNull
import kotlinx.coroutines.Dispatchers
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.command.CommandSender
import net.minestom.server.command.builder.arguments.ArgumentString
import net.minestom.server.entity.Player
import world.cepi.kstom.command.arguments.suggest
import world.cepi.kstom.command.kommand.Kommand
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.*

object Top10Command : Kommand({

    val timeFrameArgument = ArgumentString("timeFrame")
        .suggest { TimeFrame.values().map { it.name.lowercase() } }
        .map { enumValueOrNull<TimeFrame>(it.uppercase()) }

    defaultSuspending {
        runCommand(sender)
    }

    syntaxSuspending(Dispatchers.IO, timeFrameArgument) {
        val timeFrame = !timeFrameArgument
        if (timeFrame == null) {
            sender.sendMessage(Component.text("Invalid time frame", NamedTextColor.RED))
            return@syntaxSuspending
        }
        runCommand(sender, timeFrame)
    }

}, "top10", "leaderboard", "lb") {

    suspend fun runCommand(sender: CommandSender, timeFrame: TimeFrame = TimeFrame.WEEKLY) {
        val highscores = MarathonExtension.mongoStorage?.getTopHighscores(10, timeFrame.collection) ?: return


        val message = Component.text()
            .append(Component.text(centerText("${timeFrame.lyName.replaceFirstChar(Char::uppercase)} Marathon Leaderboard", bold = true), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
            .append(Component.newline())

        if (highscores.isEmpty()) {
            message.append(Component.text("\n   No scores (╯°□°）╯︵ ┻━┻\n", NamedTextColor.GRAY))

            sender.sendMessage(message.armify())
            return // early exit
        }

        var i = 1
        highscores.forEach {
            val playerUsername = UUID.fromString(it.uuid).getCachedUsername() ?: "???"

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

            val bps = (it.score.toDouble() / it.timeTaken) * 1000

            message.append(
                Component.text()
                    .append(Component.text("\n #", color).decoration(TextDecoration.BOLD, false))
                    .append(Component.text(i, color))
                    .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(playerUsername, nameColor))
                    .append(Component.space())
                    .append(Component.text(it.score, scoreColor))
                    .append(Component.text(" (${(it.timeTaken / 1000).parsed()} ", timesColor))
                    .append(Component.text("%.2f".format(bps), timesColor))
                    .append(Component.text("bps)", timesColor))
            )

            if (i == 3) message.append(Component.newline())
            i++
        }

        if (sender is Player) {

            if (!highscores.any { it.uuid == sender.uuid.toString() }) {
                val highscore = MarathonExtension.mongoStorage?.getHighscore(sender.uuid, timeFrame.collection)
                val highscorePoints = highscore?.score ?: 0
                val timeTaken = highscore?.timeTaken ?: 1L
                val placement = MarathonExtension.mongoStorage?.getPlacement(highscorePoints, timeFrame.collection) ?: 0

                val bps = (highscorePoints.toDouble() / (highscore?.timeTaken ?: 0)) * 1000

                message.append(
                    Component.text()
                        .append(Component.text(" \n\n${placement}", NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(sender.username, NamedTextColor.DARK_AQUA))
                        .append(Component.space())
                        .append(Component.text(highscorePoints, NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.text(" (${(timeTaken / 1000).parsed()} ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("%.2f".format(bps), NamedTextColor.DARK_GRAY))
                        .append(Component.text("bps)", NamedTextColor.DARK_GRAY))
                )
            }
        }

        val now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
        val tomorrow = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).plusDays(1).toEpochSecond(ZoneOffset.UTC)
        val nextWeek = LocalDateTime.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS).toEpochSecond(
            ZoneOffset.UTC)
        val nextMonth = LocalDateTime.now().with(TemporalAdjusters.firstDayOfNextMonth()).truncatedTo(ChronoUnit.DAYS).toEpochSecond(
            ZoneOffset.UTC)

        val resetSeconds = when (timeFrame) {
            TimeFrame.DAILY -> tomorrow - now
            TimeFrame.WEEKLY -> nextWeek - now
            TimeFrame.MONTHLY -> nextMonth - now
            else -> 0L
        }
        if (resetSeconds != 0L) message.append(
            Component.text()
                .append(Component.text("\n\n" + centerText("Resetting in ${resetSeconds.parsed()}"), NamedTextColor.GRAY))
        )

        sender.sendMessage(message.armify())
    }

}