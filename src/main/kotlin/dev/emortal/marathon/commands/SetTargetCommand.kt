package dev.emortal.marathon.commands

import dev.emortal.immortal.game.GameManager.game
import dev.emortal.marathon.game.MarathonGame
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.command.builder.arguments.number.ArgumentInteger
import world.cepi.kstom.command.kommand.Kommand

object SetTargetCommand : Kommand({

    onlyPlayers

    val targetArgument = ArgumentInteger("target")

    syntax(targetArgument) {
        val game = player.game as? MarathonGame
        if (game == null) {
            sender.sendMessage(Component.text("You need to be in Marathon to do this command", NamedTextColor.RED))
            return@syntax
        }

        game.target = !targetArgument
        game.passedTarget = false
        sender.sendMessage(Component.text("Set your Marathon score target to ${game.target}!", NamedTextColor.YELLOW))
    }


}, "settarget")