package dev.emortal.marathon.commands

import dev.emortal.immortal.luckperms.PermissionUtils.hasLuckPermission
import dev.emortal.marathon.MarathonExtension
import dev.emortal.marathon.db.Highscore
import dev.emortal.marathon.db.MongoStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.command.builder.arguments.ArgumentWord
import net.minestom.server.command.builder.arguments.number.ArgumentInteger
import world.cepi.kstom.command.kommand.Kommand
import java.util.*

object SetScoreCommand : Kommand({
    onlyPlayers()

    val playerArgument = ArgumentWord("username")
    val score = ArgumentInteger("score")
    val mins = ArgumentInteger("mins")
    val secs = ArgumentInteger("secs")

    syntax(playerArgument, score, mins, secs) {
        if (!sender.hasLuckPermission("marathon.setscore")) {
            sender.sendMessage(Component.text("No permission", NamedTextColor.RED))
            return@syntax
        }

        if (MarathonExtension.mongoStorage == null) {
            sender.sendMessage("Mongo storage is disabled")
            return@syntax
        }

        val username = !playerArgument

        CoroutineScope(Dispatchers.IO).launch {
            val previousHighscore = MarathonExtension.mongoStorage!!.getHighscore(UUID.fromString(username), MongoStorage.leaderboard)

            MarathonExtension.mongoStorage!!.setHighscore(
                Highscore(username, !score, (((!mins * 60) + !secs) * 1000).toLong(), previousHighscore?.timeSubmitted ?: 0),
                MongoStorage.leaderboard
            )
        }

        sender.sendMessage("Lol we set their score to ${!score} hahaha")
    }

}, "setscore")