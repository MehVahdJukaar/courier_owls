package net.mehvahdjukaar.courier_owls.owls.blocks;

import net.mehvahdjukaar.courier_owls.owls.entities.OwlAi;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlEntity;
import net.mehvahdjukaar.courier_owls.owls.nest.NestRitual;
import net.mehvahdjukaar.moonlight.api.set.wood.WoodType;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public abstract class BirdNestBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty OCCUPIED = BlockStateProperties.OCCUPIED;

    private static final int BLOCKED_EXIT_RETRY_TICKS = 40;

    private static final int SHOOED_STAY_OUT_TICKS = 600;

    private static final int WAKE_CHECK_TICKS = 100;
    private static final float WAKE_CHANCE = 0.1F;

    private final WoodType woodType;

    protected BirdNestBlock(WoodType woodType, Properties properties) {
        super(properties);
        this.woodType = woodType;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OCCUPIED, false));
    }

    public WoodType getWoodType() {
        return woodType;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OCCUPIED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BirdNestBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BirdNestBlockEntity nest)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) sayWhoLivesHere(nest, player);

        if (NestRitual.isRitualClick(level, player, stack)) {
            NestRitual.tryClaim(level, pos, player, stack);
            return InteractionResult.SUCCESS;
        }

        if (nest.isOccupied()) {
            if (!level.isClientSide()) {
                OwlEntity released = nest.releaseOccupant();
                if (released != null) released.keepOutOfNestFor(SHOOED_STAY_OUT_TICKS);
            }
            return InteractionResult.SUCCESS;
        }
        return useOnEmptyNest(nest, level, player, hand, stack);
    }

    private static void sayWhoLivesHere(BirdNestBlockEntity nest, Player player) {
        String owner = nest.getClaimantOwnerName();
        if (owner != null) {
            if (player instanceof ServerPlayer served) {
                served.sendSystemMessage(Component.translatable("message.courier_owls.nest_owner", owner), true);
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof BirdNestBlockEntity nest) || !nest.isOccupied()) {
            return;
        }

        if (nest.isSheltering()) {
            if (OwlAi.isSleepTime(level) || OwlAi.isRainingAround(level, pos)) {
                level.scheduleTick(pos, this, BirdNestBlockEntity.VISIT_TICKS);
                return;
            }
            if (random.nextFloat() >= WAKE_CHANCE) {
                level.scheduleTick(pos, this, WAKE_CHECK_TICKS);
                return;
            }
        }
        nest.releaseOccupant();
        if (nest.isOccupied()) {
            level.scheduleTick(pos, this, BLOCKED_EXIT_RETRY_TICKS);
        }
    }

    protected abstract InteractionResult useOnEmptyNest(BirdNestBlockEntity nest, Level level, Player player,
                                                            InteractionHand hand, ItemStack stack);

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof BirdNestBlockEntity nest) {
            nest.dropOccupant();
        }
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (level.getBlockEntity(pos) instanceof BirdNestBlockEntity nest) return nest.getRedstoneSignal();
        return 0;
    }
}
