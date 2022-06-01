package dev.emortal.marathon.utils

import net.kyori.adventure.text.Component
import net.minestom.server.scoreboard.Sidebar

fun Sidebar.updateOrCreateLine(id: String, content: Component, value: Int) {
    if (this.getLine(id) == null) {
        createLine(Sidebar.ScoreboardLine(id, content, value))
    } else {
        updateLineContent(id, content)
        updateLineScore(id, value)
    }
}