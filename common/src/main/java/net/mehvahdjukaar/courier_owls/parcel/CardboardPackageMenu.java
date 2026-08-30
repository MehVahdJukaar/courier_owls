package net.mehvahdjukaar.courier_owls.parcel;

import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class CardboardPackageMenu extends AbstractContainerMenu {
    public static final int NAME_LIMIT = 32;

    private static final int SLOT_SIZE = 18;
    private static final int PARCEL_X = 80;
    private static final int PARCEL_Y = 20;
    private static final int PLAYER_X = 8;
    private static final int PLAYER_Y = 74;
    private static final int HOTBAR_Y = 132;

    private final Container contents;

    @Nullable
    private final PackageTarget target;

    private final int lockedSlot;
    private String recipient;

    public static void openInHand(ServerPlayer player, InteractionHand hand) {
        HeldPackage held = new HeldPackage(player, hand);
        int locked = held.lockedInventorySlot();
        SimpleMenuProvider provider = new SimpleMenuProvider(
                (id, inventory, p) -> new CardboardPackageMenu(id, inventory, held, locked),
                player.getItemInHand(hand).getHoverName());
        PlatHelper.openCustomMenu(player, provider, buf -> {
            buf.writeVarInt(locked);
            buf.writeUtf(held.recipient(), NAME_LIMIT);
        });
    }

    public static void openPlaced(ServerPlayer player, CardboardPackageBlockEntity parcel) {
        PlatHelper.openCustomMenu(player, parcel, buf -> {
            buf.writeVarInt(-1);
            buf.writeUtf(parcel.recipient(), NAME_LIMIT);
        });
    }

    public CardboardPackageMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, null, new SimpleContainer(1), buf.readVarInt(), buf.readUtf(NAME_LIMIT));
    }

    public CardboardPackageMenu(int id, Inventory playerInventory, PackageTarget target, int lockedSlot) {
        this(id, playerInventory, target, target.contents(), lockedSlot, target.recipient());
    }

    private CardboardPackageMenu(int id, Inventory playerInventory, @Nullable PackageTarget target,
                                 Container contents, int lockedSlot, String recipient) {
        super(ParcelMod.CARDBOARD_PACKAGE_MENU.get(), id);
        checkContainerSize(contents, 1);
        this.contents = contents;
        this.target = target;
        this.lockedSlot = lockedSlot;
        this.recipient = recipient;
        contents.startOpen(playerInventory.player);

        this.addSlot(new Slot(contents, 0, PARCEL_X, PARCEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !(stack.getItem() instanceof CardboardPackageItem);
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        PLAYER_X + col * SLOT_SIZE, PLAYER_Y + row * SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(hotbarSlot(playerInventory, col, PLAYER_X + col * SLOT_SIZE, HOTBAR_Y));
        }
    }

    private Slot hotbarSlot(Inventory playerInventory, int index, int x, int y) {
        if (index != lockedSlot) return new Slot(playerInventory, index, x, y);

        return new Slot(playerInventory, index, x, y) {
            @Override
            public boolean mayPickup(Player player) {
                return false;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        };
    }

    public String recipient() {
        return recipient;
    }

    public void setRecipient(String name) {
        String trimmed = name.trim();
        if (trimmed.length() > NAME_LIMIT) trimmed = trimmed.substring(0, NAME_LIMIT);
        this.recipient = trimmed;
        if (target != null) target.setRecipient(trimmed);
    }

    @Override
    public boolean stillValid(Player player) {
        return target == null || target.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack inSlot = slot.getItem();
        ItemStack original = inSlot.copy();
        if (index == 0) {
            if (!this.moveItemStackTo(inSlot, 1, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (!this.moveItemStackTo(inSlot, 0, 1, false)) {
            return ItemStack.EMPTY;
        }

        if (inSlot.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        contents.stopOpen(player);
    }
}
