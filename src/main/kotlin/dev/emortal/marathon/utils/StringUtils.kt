package dev.emortal.marathon.utils

fun String.title(): String
    = this.split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }