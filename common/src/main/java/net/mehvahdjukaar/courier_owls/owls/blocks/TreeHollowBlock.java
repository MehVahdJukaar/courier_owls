package net.mehvahdjukaar.courier_owls.owls.blocks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.mehvahdjukaar.moonlight.api.set.wood.WoodType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

public class TreeHollowBlock extends BirdNestBlock {
    public static final BooleanProperty SEALED = BooleanProperty.create("sealed");

    public static final MapCodec<TreeHollowBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.lazyInitialized(() -> WoodType.CODEC).fieldOf("wood_type").forGetter(TreeHollowBlock::getWoodType),
            propertiesCodec()
    ).apply(i, TreeHollowBlock::new));

    public TreeHollowBlock(WoodType woodType, Properties properties) {
        super(woodType, properties);
        this.registerDefaultState(this.defaultBlockState().setValue(SEALED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SEALED);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide() || !state.getValue(SEALED)) return null;
        return (l, pos, s, be) -> openTheHole((ServerLevel) l, pos, s);
    }

    private void openTheHole(ServerLevel level, BlockPos pos, BlockState state) {
        Direction doorway = state.getValue(FACING);
        if (!isDoorwayClear(level, pos, doorway)) {
            doorway = clearSide(level, pos);
        }
        if (doorway == null) {
            if (level.getBlockEntity(pos) instanceof BirdNestBlockEntity nest) nest.forgetOccupant();
            level.setBlockAndUpdate(pos, this.getWoodType().log.defaultBlockState());
            return;
        }
        level.setBlockAndUpdate(pos, state.setValue(FACING, doorway).setValue(SEALED, false));
    }

    @Nullable
    private static Direction clearSide(ServerLevel level, BlockPos pos) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (isDoorwayClear(level, pos, side)) return side;
        }
        return null;
    }

    private static boolean isDoorwayClear(ServerLevel level, BlockPos pos, Direction side) {
        BlockPos front = pos.relative(side);
        return level.getBlockState(front).getCollisionShape(level, front).isEmpty();
    }

    @Override
    protected InteractionResult useOnEmptyNest(BirdNestBlockEntity nest, Level level, Player player,
                                                   InteractionHand hand, ItemStack stack) {
        return nest.interactWithPlayerItem(player, hand, stack, nest.slotToInteract(stack));
    }
}
