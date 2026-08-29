package net.mehvahdjukaar.courier_owls.owls.blocks;

import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class BirdNestMenu extends AbstractContainerMenu {
    private static final int SLOT_SIZE = 18;
    private static final int BACKGROUND_WIDTH = 176;
    private static final int NEST_COLS = 2;
    private static final int NEST_Y = 18;
    private static final int NEST_X = (BACKGROUND_WIDTH - NEST_COLS * SLOT_SIZE) / 2 + 1;
    private static final int PLAYER_X = 8;
    private static final int PLAYER_Y = 68;
    private static final int HOTBAR_Y = 126;

    private final Container nest;

    public BirdNestMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, new SimpleContainer(BirdNestBlockEntity.SLOTS));
    }

    public BirdNestMenu(int id, Inventory playerInventory, Container nest) {
        super(OwlMod.BIRD_NEST_MENU.get(), id);
        checkContainerSize(nest, BirdNestBlockEntity.SLOTS);
        this.nest = nest;
        nest.startOpen(playerInventory.player);

        for (int i = 0; i < BirdNestBlockEntity.SLOTS; i++) {
            int col = i % NEST_COLS;
            int row = i / NEST_COLS;
            this.addSlot(new Slot(nest, i, NEST_X + col * SLOT_SIZE, NEST_Y + row * SLOT_SIZE));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        PLAYER_X + col * SLOT_SIZE, PLAYER_Y + row * SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, PLAYER_X + col * SLOT_SIZE, HOTBAR_Y));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return nest.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack inSlot = slot.getItem();
        ItemStack original = inSlot.copy();
        boolean fromNest = index < BirdNestBlockEntity.SLOTS;
        if (fromNest) {
            if (!this.moveItemStackTo(inSlot, BirdNestBlockEntity.SLOTS, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (!this.moveItemStackTo(inSlot, 0, BirdNestBlockEntity.SLOTS, false)) {
            return ItemStack.EMPTY;
        }

        if (inSlot.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        nest.stopOpen(player);
    }
}
