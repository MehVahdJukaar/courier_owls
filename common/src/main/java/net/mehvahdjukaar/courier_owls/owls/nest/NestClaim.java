package net.mehvahdjukaar.courier_owls.owls.nest;

import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.mehvahdjukaar.courier_owls.owls.blocks.BirdNestBlockEntity;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public class NestClaim {
    private static final Predicate<Holder<PoiType>> ANY_NEST = holder ->
            holder.is(OwlMod.TREE_HOLLOW_POI.getKey()) || holder.is(OwlMod.BIRD_HOUSE_POI.getKey());

    public static final int LEASH = 128;

    private static final int SEARCH_RADIUS = 48;

    @Nullable
    public static BlockPos of(OwlEntity owl) {
        return owl.getBrain().getMemory(OwlMod.NEST_POS.get()).orElse(null);
    }

    public static boolean claim(OwlEntity owl, BlockPos pos) {
        BirdNestBlockEntity nest = nestAt(owl.level(), pos);
        if (nest == null || !(owl.level() instanceof ServerLevel level)) {
            return false;
        }
        if (nest.isOccupied()) {
            nest.releaseOccupant();
        }

        level.getPoiManager().release(pos);
        nest.setClaimant(owl.getUUID(), owl.isTame() ? ownerName(owl) : null);
        owl.getBrain().setMemory(OwlMod.NEST_POS.get(), pos);

        owl.setPersistenceRequired();
        return true;
    }

    public static void moveIn(ServerLevel level, BlockPos pos) {
        level.getPoiManager().take(ANY_NEST, (holder, candidate) -> candidate.equals(pos), pos, 1);
    }

    public static void abandon(OwlEntity owl) {
        BlockPos pos = of(owl);
        owl.getBrain().eraseMemory(OwlMod.NEST_POS.get());
        if (pos == null) {
            return;
        }
        if (owl.level() instanceof ServerLevel level && level.getPoiManager().exists(pos, ANY_NEST)) {
            level.getPoiManager().release(pos);
        }
        BirdNestBlockEntity nest = nestAt(owl.level(), pos);
        if (nest != null && owl.getUUID().equals(nest.getClaimant())) {
            nest.setClaimant(null, null);
        }
    }

    public static void validate(OwlEntity owl) {
        BlockPos pos = of(owl);
        if (pos == null) {
            return;
        }
        if (!pos.closerThan(owl.blockPosition(), LEASH)) {
            abandon(owl);
            return;
        }
        if (!owl.level().isLoaded(pos)) {
            return;
        }
        BirdNestBlockEntity nest = nestAt(owl.level(), pos);
        if (nest == null || !owl.getUUID().equals(nest.getClaimant())) {
            owl.getBrain().eraseMemory(OwlMod.NEST_POS.get());
        }
    }

    @Nullable
    public static BlockPos findFreeHollow(ServerLevel level, OwlEntity owl) {
        return level.getPoiManager().findClosest(
                        holder -> holder.is(OwlMod.TREE_HOLLOW_POI.getKey()),
                        pos -> isUnclaimed(level, pos),
                        owl.blockPosition(), SEARCH_RADIUS, PoiManager.Occupancy.HAS_SPACE)
                .orElse(null);
    }

    private static boolean isUnclaimed(Level level, BlockPos pos) {
        BirdNestBlockEntity nest = nestAt(level, pos);
        return nest != null && nest.getClaimant() == null;
    }

    @Nullable
    public static BirdNestBlockEntity nestAt(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof BirdNestBlockEntity nest ? nest : null;
    }

    @Nullable
    private static String ownerName(OwlEntity owl) {
        LivingEntity owner = owl.getOwner();
        return owner == null ? null : owner.getName().getString();
    }
}
