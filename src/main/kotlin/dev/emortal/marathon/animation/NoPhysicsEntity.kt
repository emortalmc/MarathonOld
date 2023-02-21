package dev.emortal.marathon.animation

import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType

class NoPhysicsEntity(entityType: EntityType) : Entity(entityType) {

    init {
        hasPhysics = false
    }

}