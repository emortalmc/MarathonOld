package dev.emortal.marathon.utils

import dev.emortal.acquaintance.RelationshipManager.setCachedUsername
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private const val mojangUrl = "https://sessionserver.mojang.com/session/minecraft/profile/"
fun usernameFromUUID(uuid: String): String? {
    val req = HttpRequest.newBuilder()
        .uri(URI.create(mojangUrl + uuid))
        .header("Content-Type", "application/json")
        .GET()
        .build()

    val client = HttpClient.newHttpClient()

    val res = client.send(req, HttpResponse.BodyHandlers.ofString())

    if (res.statusCode() != 200) {
        return null
    }

    val index = res.body().indexOf('"', 60)
    val username = res.body().take(index).takeLast(index - 59)
    uuid.setCachedUsername(username)
    return username
}