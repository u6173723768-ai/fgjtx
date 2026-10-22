package com.mediaclient.schematic;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** Moves required block stacks with SWAP clicks, never dropping or deleting an item. */
public final class InventoryManager {
    private InventoryManager() { }

    public static int prepareBlock(Minecraft client, Block block) {
        Player player = client.player;
        if (player == null) return -1;
        Inventory inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        if (matches(inventory.getItem(selected), block)) return selected;
        for (int slot = 0; slot < 9; slot++) if (matches(inventory.getItem(slot), block)) {
            inventory.setSelectedSlot(slot);
            return slot;
        }
        int sourceSlot = -1;
        for (int slot = 9; slot < 36; slot++) if (matches(inventory.getItem(slot), block)) { sourceSlot = slot; break; }
        if (sourceSlot < 0 || client.gameMode == null) return -1;
        int destination = -1;
        for (int slot = 0; slot < 9; slot++) if (inventory.getItem(slot).isEmpty()) { destination = slot; break; }
        if (destination < 0) destination = selected;
        client.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, sourceSlot, destination, ClickType.SWAP, player);
        inventory.setSelectedSlot(destination);
        return matches(inventory.getItem(destination), block) ? destination : -1;
    }

    public static int count(Minecraft client, Block block) {
        if (client.player == null) return 0;
        int count = 0;
        Inventory inventory = client.player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (matches(stack, block)) count += stack.getCount();
        }
        return count;
    }

    public static boolean matches(ItemStack stack, Block block) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == block;
    }
}
