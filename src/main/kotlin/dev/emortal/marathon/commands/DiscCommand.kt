package dev.emortal.marathon.commands

import dev.emortal.marathon.commands.DiscCommand.performCommand
import dev.emortal.marathon.commands.DiscCommand.stopPlaying
import dev.emortal.marathon.commands.DiscCommand.suggestions
import dev.emortal.marathon.gui.MusicPlayerInventory
import dev.emortal.marathon.utils.MusicDisc
import dev.emortal.nbstom.NBS
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.SoundStop
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import world.cepi.kstom.Manager
import world.cepi.kstom.adventure.asMini
import world.cepi.kstom.command.arguments.literal
import world.cepi.kstom.command.arguments.suggest
import world.cepi.kstom.command.kommand.Kommand
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.stream.Collectors
import kotlin.io.path.nameWithoutExtension
import kotlin.math.roundToLong

object DiscCommand : Kommand({

    onlyPlayers

    // If no arguments given, open inventory
    default {
        player.playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 1f, 2f))
        player.openInventory(MusicPlayerInventory.inventory)
    }

    val stop by literal

    val discArgument = ArgumentType.StringArray("disc").suggest {
        suggestions
    }


    syntax(stop) {
        stopPlaying(player)
    }

    syntax(discArgument) {
        performCommand(player, (!discArgument).joinToString(separator = " "))
    }
}, "disc", "music") {

    val stopPlayingTaskMap = HashMap<Player, Task>()
    private val playingDiscTag = Tag.Integer("playingDisc")

    private var nbsSongs: List<String> = listOf()
    private var suggestions: List<String> = listOf()

    fun refreshSongs() {
        try {
            nbsSongs = Files.list(Path.of("./nbs/")).collect(Collectors.toUnmodifiableList()).map { it.nameWithoutExtension }
            suggestions = MusicDisc.values().map { it.shortName } + nbsSongs
        } catch (e: Exception) {
            nbsSongs = listOf()
        }
    }

    fun stopPlaying(player: Player) {
        val discValues = MusicDisc.values()
        val playingDisc = player.getTag(playingDiscTag)?.let { discValues[it] }

        playingDisc?.sound?.let {
            player.stopSound(SoundStop.named(it))
            player.removeTag(playingDiscTag)
        }

        stopPlayingTaskMap[player]?.cancel()
        NBS.stopPlaying(player)
    }

    fun performCommand(player: Player, disc: String) {

        val discValues = MusicDisc.values()
        val playingDisc = player.getTag(playingDiscTag)?.let { discValues[it] }

        playingDisc?.sound?.let {
            player.stopSound(SoundStop.named(it))
            player.removeTag(playingDiscTag)
        }

        stopPlayingTaskMap[player]?.cancel()
        NBS.stopPlaying(player)

        var discName: String
        try {
            val nowPlayingDisc = MusicDisc.valueOf("MUSIC_DISC_${disc.uppercase()}")

            discName = nowPlayingDisc.description

            player.setTag(playingDiscTag, discValues.indexOf(nowPlayingDisc))
            player.playSound(Sound.sound(nowPlayingDisc.sound, Sound.Source.MASTER, 1f, 1f), Sound.Emitter.self())

            stopPlayingTaskMap[player] = Manager.scheduler.buildTask {
                stopPlaying(player)
            }.delay(Duration.ofSeconds(nowPlayingDisc.length.toLong())).schedule()
        } catch (e: IllegalArgumentException) {
            if (!nbsSongs.contains(disc)) {
                player.sendMessage(Component.text("Invalid song", NamedTextColor.RED))
                return
            }

            val nbs = NBS(Path.of("./nbs/${disc}.nbs"))
            NBS.playWithParticles(nbs, player)

            if (disc == "DJ Got Us Fallin' in Love") {
                Manager.scheduler.buildTask {
                    player.sendMessage("Creeper?")
                }.delay(Duration.ofMillis(3850)).schedule()
                Manager.scheduler.buildTask {
                    player.sendMessage("Aww man")
                }.delay(Duration.ofMillis(5900)).schedule()
            }

            stopPlayingTaskMap[player] = Manager.scheduler.buildTask {
                stopPlaying(player)
            }.delay(Duration.ofSeconds((nbs.length / nbs.tps).roundToLong())).schedule()

            discName = "${nbs.originalAuthor.ifEmpty { nbs.author }} - ${nbs.songName}"
        }

        player.sendActionBar("<gray>Playing: <aqua>${discName}</aqua>".asMini())
    }


}