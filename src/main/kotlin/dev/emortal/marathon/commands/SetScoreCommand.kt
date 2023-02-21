package dev.emortal.marathon.commands

import dev.emortal.immortal.luckperms.PermissionUtils.hasLuckPermission
import dev.emortal.marathon.MarathonMain
import dev.emortal.marathon.db.Highscore
import dev.emortal.marathon.db.MongoStorage
import dev.emortal.marathon.utils.TimeFrame
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentWord
import net.minestom.server.command.builder.arguments.number.ArgumentInteger
import java.util.*

object SetScoreCommand : Command("setscore") {

    init {
        val playerArgument = ArgumentWord("username")
        val score = ArgumentInteger("score")
        val mins = ArgumentInteger("mins")
        val secs = ArgumentInteger("secs")

        addConditionalSyntax({ sender, _ ->
            sender.hasLuckPermission("marathon.setscore")
        }, { sender, ctx ->
            if (!sender.hasLuckPermission("marathon.setscore")) {
                sender.sendMessage(Component.text("No permission", NamedTextColor.RED))
                return@addConditionalSyntax
            }

            if (MarathonMain.mongoStorage == null) {
                sender.sendMessage("Mongo storage is disabled")
                return@addConditionalSyntax
            }

            val username = ctx.get(playerArgument)

            runBlocking {
                launch {
                    TimeFrame.values().forEach {
                        val collection = when (it) {
                            TimeFrame.LIFETIME -> MongoStorage.leaderboard
                            TimeFrame.MONTHLY -> MongoStorage.monthly
                            TimeFrame.WEEKLY -> MongoStorage.weekly
                        }

                        val previousHighscore = MarathonMain.mongoStorage!!.getHighscore(UUID.fromString(username), collection)

                        MarathonMain.mongoStorage!!.setHighscore(
                            Highscore(username, ctx.get(score), (((ctx.get(mins) * 60) + ctx.get(secs)) * 1000).toLong(), previousHighscore?.timeSubmitted ?: 0),
                            collection
                        )
                    }
                }
            }

            sender.sendMessage("Lol we set ${username}'s score to ${ctx.get(score)} hahaha")
        }, playerArgument, score, mins, secs)
    }

}