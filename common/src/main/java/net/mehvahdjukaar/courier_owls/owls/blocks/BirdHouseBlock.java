package net.mehvahdjukaar.courier_owls.owls.blocks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.mehvahdjukaar.moonlight.api.set.wood.WoodType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public class BirdHouseBlock extends BirdNestBlock {
    public static final MapCodec<BirdHouseBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.lazyInitialized(() -> WoodType.CODEC).fieldOf("wood_type").forGetter(BirdHouseBlock::getWoodType),
            propertiesCodec()
    ).apply(i, BirdHouseBlock::new));

    public BirdHouseBlock(WoodType woodType, Properties properties) {
        super(woodType, properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useOnEmptyNest(BirdNestBlockEntity nest, Level level, Player player,
                                                   InteractionHand hand, ItemStack stack) {
        if (!level.isClientSide()) player.openMenu(nest);
        return InteractionResult.SUCCESS;
    }
}
