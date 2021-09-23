package dev.emortal.marathon.utils

fun <T> List<Pair<T, T>>.firsts() = map { it.first }
fun <T> List<Pair<T, T>>.seconds() = map { it.second }