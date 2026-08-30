package net.mehvahdjukaar.courier_owls.parcel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class CardboardPackageBlockEntity extends RandomizableContainerBlockEntity implements PackageTarget {
    private static final String RECIPIENT_TAG = "Recipient";

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private String recipient = "";

    public CardboardPackageBlockEntity(BlockPos pos, BlockState state) {
        super(ParcelMod.CARDBOARD_PACKAGE_TILE.get(), pos, state);
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    protected Component getDefaultName() {
        return this.getBlockState().getBlock().getName();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> itemsIn) {
        this.items = itemsIn;
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return !(stack.getItem() instanceof CardboardPackageItem);
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory playerInventory) {
        return new CardboardPackageMenu(id, playerInventory, this, -1);
    }

    @Override
    public Container contents() {
        return this;
    }

    @Override
    public String recipient() {
        return recipient;
    }

    @Override
    public void setRecipient(String name) {
        this.recipient = name.trim();
        this.setChanged();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(input)) {
            ContainerHelper.loadAllItems(input, this.items);
        }
        this.recipient = input.getStringOr(RECIPIENT_TAG, "");
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!this.trySaveLootTable(output)) {
            ContainerHelper.saveAllItems(output, this.items, false);
        }
        if (!recipient.isEmpty()) output.putString(RECIPIENT_TAG, recipient);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!recipient.isEmpty()) components.set(ParcelMod.RECIPIENT.get(), recipient);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        this.recipient = components.getOrDefault(ParcelMod.RECIPIENT.get(), "");
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard(RECIPIENT_TAG);
    }
}
