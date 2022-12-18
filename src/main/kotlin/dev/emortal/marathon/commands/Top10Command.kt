package dev.emortal.marathon.commands

import dev.emortal.acquaintance.RelationshipManager.getCachedUsername
import dev.emortal.immortal.util.armify
import dev.emortal.immortal.util.centerText
import dev.emortal.immortal.util.parsed
import dev.emortal.marathon.MarathonMain
import dev.emortal.marathon.utils.TimeFrame
import dev.emortal.marathon.utils.enumValueOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.command.CommandSender
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentString
import net.minestom.server.command.builder.suggestion.SuggestionEntry
import net.minestom.server.entity.Player
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object Top10Command : Command("leaderboard", "top10", "lb") {

    init {
        val timeFrameArgument = ArgumentString("timeFrame")
            .setSuggestionCallback { sender, context, suggestion ->
                TimeFrame.values().forEach { suggestion.addEntry(SuggestionEntry(it.name.lowercase())) }
            }
            .map { enumValueOrNull<TimeFrame>(it.uppercase()) }

        addSyntax({ sender, context -> runBlocking {
            val timeFrame = context.get(timeFrameArgument)
            if (timeFrame == null) {
                sender.sendMessage(Component.text("Invalid time frame", NamedTextColor.RED))
                return@runBlocking
            }

            launch {
                runCommand(sender, timeFrame)
            }
        }}, timeFrameArgument)

        setDefaultExecutor { sender, context ->
            runBlocking {
                launch {
                    runCommand(sender)
                }
            }
        }
    }

    suspend fun runCommand(sender: CommandSender, timeFrame: TimeFrame = TimeFrame.MONTHLY) {
        val highscores = MarathonMain.mongoStorage?.getTopHighscores(10, timeFrame.collection)
        if (highscores == null) {
            sender.sendMessage("Leaderboards are disabled in marathon.json")
            return
        }

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
            val playerUsername = it.uuid.getCachedUsername() ?: "???"

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

        // Use run so it doesn't block message
        if (sender is Player) run {

            if (!highscores.any { it.uuid == sender.uuid.toString() }) {
                val highscore = MarathonMain.mongoStorage?.getHighscore(sender.uuid, timeFrame.collection)
                val highscorePoints = highscore?.score ?: return@run
                val timeTaken = highscore.timeTaken
                val placement = MarathonMain.mongoStorage?.getPlacementByScore(highscorePoints, timeFrame.collection) ?: 0

                val bps = (highscorePoints.toDouble() / highscore.timeTaken) * 1000

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
        val nextWeek = LocalDateTime.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS).toEpochSecond(ZoneOffset.UTC)
        val nextMonth = LocalDateTime.now().with(TemporalAdjusters.firstDayOfNextMonth()).truncatedTo(ChronoUnit.DAYS).toEpochSecond(ZoneOffset.UTC)


        val resetSeconds = when (timeFrame) {
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