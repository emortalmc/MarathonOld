package dev.emortal.marathon.animation

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType

class NoPhysicsEntity(entityType: EntityType) : Entity(entityType) {

    init {
        hasPhysics = false
    }

    override fun updateVelocity(wasOnGround: Boolean, flying: Boolean, positionBeforeMove: Pos?, newVelocity: Vec?) {
        this.velocity = newVelocity
            ?.mul(MinecraftServer.TICK_PER_SECOND.toDouble())
            ?.apply(Vec.Operator.EPSILON)
    }

}