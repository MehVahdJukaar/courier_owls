package net.mehvahdjukaar.courier_owls.owls.nest;

import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.mehvahdjukaar.courier_owls.owls.blocks.BirdNestBlock;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

public class NestRitual {
    private static final int FED_WINDOW = 80;
    private static final int OWL_RANGE = 16;
    private static final int PARTICLES = 12;

    public static boolean isRitualClick(Level level, Player player, ItemStack stack) {
        return stack.is(OwlMod.OWL_FOOD) && !ownedOwlsNear(level, player).isEmpty();
    }

    public static boolean tryClaim(Level level, BlockPos pos, Player player, ItemStack stack) {
        if (level.isClientSide()) {
            return false;
        }
        OwlEntity owl = pickFedOwl(level, player);
        if (owl == null) {
            if (player instanceof ServerPlayer served) {
                served.sendSystemMessage(Component.translatable("message.courier_owls.nest_needs_fed_owl"), true);
            }
            return false;
        }
        if (!NestClaim.claim(owl, pos)) {
            return false;
        }
        VisitNest.sendHome(owl);
        if (player instanceof ServerPlayer served) {
            served.sendSystemMessage(Component.translatable("message.courier_owls.nest_given",
                    owl.getDisplayName()), true);
        }
        celebrate((ServerLevel) level, pos.relative(level.getBlockState(pos).getValue(BirdNestBlock.FACING)));
        return true;
    }

    @Nullable
    private static OwlEntity pickFedOwl(Level level, Player player) {
        long now = level.getGameTime();
        Comparator<OwlEntity> pickOrder = Comparator
                .comparingLong((OwlEntity owl) -> owl.lastFedTime()).reversed()
                .thenComparing(Comparator.comparing((OwlEntity owl) -> owl.hasLineOfSight(player)).reversed())
                .thenComparingDouble(owl -> owl.distanceToSqr(player));
        return ownedOwlsNear(level, player).stream()
                .filter(owl -> !owl.isBaby())
                .filter(owl -> now - owl.lastFedTime() <= FED_WINDOW)
                .min(pickOrder)
                .orElse(null);
    }

    private static List<OwlEntity> ownedOwlsNear(Level level, Player player) {
        return level.getEntitiesOfClass(OwlEntity.class, player.getBoundingBox().inflate(OWL_RANGE),
                owl -> owl.isOwnedBy(player));
    }

    private static void celebrate(ServerLevel level, BlockPos doorway) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                doorway.getX() + 0.5, doorway.getY() + 0.5, doorway.getZ() + 0.5, PARTICLES, 0.4, 0.4, 0.4, 0.0);
        level.playSound(null, doorway, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.2F);
    }
}
