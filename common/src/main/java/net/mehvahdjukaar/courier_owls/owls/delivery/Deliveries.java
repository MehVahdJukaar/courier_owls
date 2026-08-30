package net.mehvahdjukaar.courier_owls.owls.delivery;

import net.mehvahdjukaar.courier_owls.compat.SupplementariesCompat;
import net.mehvahdjukaar.courier_owls.configs.CommonConfigs;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlEntity;
import net.mehvahdjukaar.courier_owls.parcel.CardboardPackageItem;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.mehvahdjukaar.courier_owls.owls.nest.NestClaim;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class Deliveries {
    public static final int CONFIRM_WINDOW_TICKS = 4 * 20;

    private static final boolean SUPPLEMENTARIES = PlatHelper.isModLoaded("supplementaries");

    @Nullable
    public static String recipientOf(ItemStack stack) {
        if (stack.getItem() instanceof CardboardPackageItem) {
            String recipient = CardboardPackageItem.recipientOf(stack);
            return recipient.isEmpty() ? null : recipient;
        }
        return SUPPLEMENTARIES ? SupplementariesCompat.presentRecipient(stack) : null;
    }

    @Nullable
    public static InteractionResult onRightClick(OwlEntity owl, Player player, InteractionHand hand) {
        boolean clientSide = owl.level().isClientSide();
        if (!owl.getTalonItem().isEmpty() && mayTakeParcel(owl, player)) {
            if (!clientSide) {
                handOver(owl, player);
            }
            return InteractionResult.SUCCESS;
        }
        if (!owl.isTame() || !owl.isOwnedBy(player)) {
            return null;
        }
        ItemStack held = player.getItemInHand(hand);

        if (!player.isShiftKeyDown() || held.isEmpty() || owl.isOrderedToSit() || owl.isBaby()) {
            return null;
        }
        if (!clientSide) {
            accept((ServerLevel) owl.level(), owl, player, hand, held);
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean mayTakeParcel(OwlEntity owl, Player player) {
        if (owl.isTame() && owl.isOwnedBy(player)) {
            return true;
        }
        Delivery delivery = owl.delivery();
        return delivery != null && delivery.leg() == Delivery.Leg.ATTEND_DELIVERING
                && delivery.addressee().filter(player.getUUID()::equals).isPresent();
    }

    private static void accept(ServerLevel level, OwlEntity owl, Player player, InteractionHand hand, ItemStack held) {
        Plan plan = plan(level, owl, held, level.getGameTime() + CONFIRM_WINDOW_TICKS);
        if (plan.refusal() != null) {
            if (player instanceof ServerPlayer served) served.sendSystemMessage(plan.refusal(), true);
            return;
        }
        owl.setTalonItem(held);
        player.setItemInHand(hand, ItemStack.EMPTY);
        owl.setDelivery(plan.delivery());
        owl.attend(player, CONFIRM_WINDOW_TICKS);
        exchangeSound(owl);
    }

    private record Plan(@Nullable Delivery delivery, @Nullable Component refusal) {
        static Plan going(Delivery delivery) {
            return new Plan(delivery, null);
        }

        static Plan refused(String key, Object... args) {
            return new Plan(null, Component.translatable(key, args).withStyle(ChatFormatting.RED));
        }
    }

    private static Plan plan(ServerLevel level, OwlEntity owl, ItemStack held, long confirmEndsAt) {
        String recipient = recipientOf(held);
        if (recipient != null) {
            return planToPlayer(level, recipient, confirmEndsAt);
        }
        if (NestClaim.of(owl) == null) {
            return Plan.refused("message.courier_owls.delivery_nowhere");
        }
        return Plan.going(Delivery.toHollow(confirmEndsAt));
    }

    private static Plan planToPlayer(ServerLevel level, String recipient, long confirmEndsAt) {
        Player addressee = level.getServer().getPlayerList().getPlayerByName(recipient);
        if (addressee == null) {
            return Plan.refused("message.courier_owls.delivery_no_such_player", recipient);
        }
        if (addressee.level() != level) {
            return Plan.refused("message.courier_owls.delivery_other_dimension", recipient);
        }
        return Plan.going(Delivery.toPlayer(addressee.getUUID(), confirmEndsAt));
    }

    private static void handOver(OwlEntity owl, Player player) {
        ItemStack stack = owl.getTalonItem();
        owl.setTalonItem(ItemStack.EMPTY);
        owl.setDelivery(null);
        owl.stopAttending();
        player.getInventory().placeItemBackInInventory(stack);
        exchangeSound(owl);
    }

    public static void dropParcel(OwlEntity owl) {
        if (owl.level() instanceof ServerLevel level) owl.spawnAtLocation(level, owl.getTalonItem());
        owl.setTalonItem(ItemStack.EMPTY);
    }

    public static void dropParcelIfSatDown(OwlEntity owl) {
        if (owl.isInSittingPose() && !owl.getTalonItem().isEmpty()) {
            dropParcel(owl);
        }
    }

    public static void endErrandIfEmptyHanded(OwlEntity owl) {
        if (owl.isDelivering() && owl.getTalonItem().isEmpty()) {
            owl.setDelivery(null);
        }
    }

    public static void announceDeparture(OwlEntity owl, Player addressee) {
        LivingEntity owner = owl.getOwner();
        if (!CommonConfigs.ANNOUNCE_DELIVERIES.get() || owner == null) {
            return;
        }
        addressee.sendSystemMessage(Component.translatable("message.courier_owls.delivery_incoming",
                owner.getDisplayName()));
    }

    public static void announceArrival(OwlEntity owl, Player addressee) {
        if (!CommonConfigs.ANNOUNCE_DELIVERIES.get()) {
            return;
        }
        addressee.sendSystemMessage(Component.translatable("message.courier_owls.delivery_arrived",
                owl.getDisplayName()));

        addressee.level().playSound(null, addressee.getX(), addressee.getY(), addressee.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8F, 1.2F);
    }

    private static void exchangeSound(OwlEntity owl) {
        owl.level().playSound(null, owl.blockPosition(), SoundEvents.ITEM_FRAME_ADD_ITEM,
                SoundSource.NEUTRAL, 1.0F, owl.getRandom().nextFloat() * 0.10F + 0.95F);
    }
}
