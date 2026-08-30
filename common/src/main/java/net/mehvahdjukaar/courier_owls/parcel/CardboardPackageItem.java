package net.mehvahdjukaar.courier_owls.parcel;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

public class CardboardPackageItem extends BlockItem {
    public CardboardPackageItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static String recipientOf(ItemStack stack) {
        return stack.getOrDefault(ParcelMod.RECIPIENT.get(), "");
    }

    public static void setRecipient(ItemStack stack, String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            stack.remove(ParcelMod.RECIPIENT.get());
        } else {
            stack.set(ParcelMod.RECIPIENT.get(), trimmed);
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();

        if (player != null && !player.isSecondaryUseActive()) return InteractionResult.PASS;
        return super.useOn(context);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            CardboardPackageMenu.openInHand(serverPlayer, hand);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        String recipient = recipientOf(stack);
        if (!recipient.isEmpty()) {
            tooltip.accept(Component.translatable("message.courier_owls.package_for", recipient)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
