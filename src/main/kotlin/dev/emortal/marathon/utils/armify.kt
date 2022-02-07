package dev.emortal.marathon.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration

fun ComponentLike.armify(length: Int = 75): Component {
    return Component.text()
        .append(Component.text(" ".repeat(length) + "\n", NamedTextColor.DARK_GRAY, TextDecoration.STRIKETHROUGH))
        .append(this)
        .append(Component.text("\n" + " ".repeat(length), NamedTextColor.DARK_GRAY, TextDecoration.STRIKETHROUGH))
        .build()
}