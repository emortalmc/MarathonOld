package dev.emortal.marathon

import dev.emortal.immortal.game.GameManager.game
import dev.emortal.immortal.inventory.GUI
import dev.emortal.marathon.game.MarathonGame
import dev.emortal.marathon.utils.title
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import world.cepi.kstom.adventure.noItalic
import world.cepi.kstom.item.item
import world.cepi.kstom.item.withMeta
import world.cepi.kstom.util.get
import world.cepi.kstom.util.set
import world.cepi.kstom.util.setItemStacks

class SoundSettingGUI : GUI() {

    companion object {
        val chestTypes = arrayOf(
            InventoryType.CHEST_1_ROW,
            InventoryType.CHEST_2_ROW,
            InventoryType.CHEST_3_ROW,
            InventoryType.CHEST_4_ROW,
            InventoryType.CHEST_5_ROW,
            InventoryType.CHEST_6_ROW,
        )

        val soundEventIdTag = Tag.Integer("soundEventId")
    }

    override fun createInventory(): Inventory {


        val itemStackMap = mutableMapOf<Int, ItemStack>()

        var i = 0
        SoundEvent.values().forEach {
            if (it.name().startsWith("minecraft:block.note_block", true)) {
                itemStackMap[i] = item(Material.NOTE_BLOCK) {
                    displayName(
                        Component.text()
                            .append(Component.text(it.name().split(".")[2].replace("_", " ").title(), NamedTextColor.GOLD))
                            .append(Component.text(" (Noteblock)", NamedTextColor.DARK_GRAY))
                            .build()
                            .noItalic()
                    )

                    setTag(soundEventIdTag, it.id())

                }

                i++
            }
        }

        val invType = chestTypes.first { it.size > itemStackMap.size }
        val inventory = Inventory(invType, "Select sound effect")

        inventory.setItemStacks(itemStackMap)

        inventory.addInventoryCondition { player, slot, clickType, inventoryConditionResult ->
            inventoryConditionResult.isCancel = true

            val clickedItem = inventory[slot]
            if (clickedItem.material == Material.AIR) return@addInventoryCondition

            val game = player.game as? MarathonGame ?: return@addInventoryCondition
            val id = clickedItem.getTag(soundEventIdTag) ?: return@addInventoryCondition

            game.pointSoundEffect = SoundEvent.fromId(id)!!

            player.sendMessage(
                Component.text()
                    .append(Component.text("Set point sound effect to ", NamedTextColor.GRAY))
                    .append(Component.text(game.pointSoundEffect.name().split(".")[2].replace("_", " ").title(), NamedTextColor.GOLD, TextDecoration.BOLD))
            )
        }

        return inventory
    }

}