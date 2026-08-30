package net.mehvahdjukaar.courier_owls.parcel;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.List;

public class HeldPackage implements PackageTarget {
    private final Player player;
    private final InteractionHand hand;
    private final ItemStack parcel;
    private boolean loading = true;
    private final SimpleContainer contents = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            if (!loading) store(this.getItem(0));
        }
    };

    public HeldPackage(Player player, InteractionHand hand) {
        this.player = player;
        this.hand = hand;
        this.parcel = player.getItemInHand(hand);

        NonNullList<ItemStack> stored = NonNullList.withSize(1, ItemStack.EMPTY);
        parcel.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(stored);
        contents.setItem(0, stored.get(0));

        loading = false;
    }

    private void store(ItemStack stack) {
        if (stack.isEmpty()) {
            parcel.remove(DataComponents.CONTAINER);
        } else {
            parcel.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(stack)));
        }
    }

    public int lockedInventorySlot() {
        return hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : -1;
    }

    @Override
    public Container contents() {
        return contents;
    }

    @Override
    public String recipient() {
        return CardboardPackageItem.recipientOf(parcel);
    }

    @Override
    public void setRecipient(String name) {
        CardboardPackageItem.setRecipient(parcel, name);
    }

    @Override
    public boolean stillValid(Player player) {
        return player == this.player && player.getItemInHand(hand) == parcel;
    }
}
