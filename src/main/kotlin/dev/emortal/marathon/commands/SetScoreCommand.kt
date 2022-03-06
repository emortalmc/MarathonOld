package dev.emortal.marathon.commands

import dev.emortal.acquaintance.AcquaintanceExtension
import dev.emortal.immortal.luckperms.PermissionUtils.hasLuckPermission
import dev.emortal.marathon.MarathonExtension
import dev.emortal.marathon.db.Highscore
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.command.builder.arguments.ArgumentWord
import net.minestom.server.command.builder.arguments.number.ArgumentInteger
import world.cepi.kstom.command.kommand.Kommand
import java.util.*

object SetScoreCommand : Kommand({
    onlyPlayers

    val playerArgument = ArgumentWord("username")
    val score = ArgumentInteger("score")
    val mins = ArgumentInteger("mins")
    val secs = ArgumentInteger("secs")

    syntax(playerArgument, score, mins, secs) {
        if (!player.hasLuckPermission("marathon.setscore")) {
            player.sendMessage(Component.text("No permission", NamedTextColor.RED))
            return@syntax
        }

        val username = !playerArgument
        val uuid = AcquaintanceExtension.playerCache.filterValues { it == username }.firstNotNullOfOrNull { it.key }
            ?: return@syntax

        MarathonExtension.storage!!.setHighscore(
            UUID.fromString(uuid),
            Highscore(!score, (((!mins * 60) + !secs) * 1000).toLong())
        )

        sender.sendMessage("Lol we set their score to ${!score} hahaha")
    }

}, "setscore")