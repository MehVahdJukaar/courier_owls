package net.mehvahdjukaar.courier_owls.owls.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.mehvahdjukaar.courier_owls.owls.blocks.BirdNestBlock;
import net.mehvahdjukaar.courier_owls.owls.blocks.BirdNestBlockEntity;
import net.mehvahdjukaar.courier_owls.owls.blocks.TreeHollowBlock;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlType;
import net.minecraft.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TreeHollowDecorator extends TreeDecorator {
    public static final MapCodec<TreeHollowDecorator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").forGetter(d -> d.hollow),
            Codec.floatRange(0.0F, 1.0F).fieldOf("probability").forGetter(d -> d.probability),
            Codec.floatRange(0.0F, 1.0F).fieldOf("owl_probability").forGetter(d -> d.owlProbability)
    ).apply(i, TreeHollowDecorator::new));

    private static final int LOWEST_LOG = 1;
    private static final int HIGHEST_LOG = 6;

    private final Block hollow;
    private final float probability;
    private final float owlProbability;

    public TreeHollowDecorator(Block hollow, float probability, float owlProbability) {
        this.hollow = hollow;
        this.probability = probability;
        this.owlProbability = owlProbability;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return OwlMod.TREE_HOLLOW_DECORATOR.get();
    }

    @Override
    public void place(Context context) {
        RandomSource random = context.random();
        if (random.nextFloat() >= probability) return;

        List<BlockPos> candidates = trunkLogs(context);
        if (candidates.isEmpty()) return;

        Util.shuffle(candidates, random);
        for (BlockPos pos : candidates) {
            Direction doorway = openFace(context, pos, random);
            if (doorway == null) continue;

            boolean withOwl = random.nextFloat() < owlProbability && context.level() instanceof WorldGenLevel;

            context.setBlock(pos, hollow.defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, doorway)
                    .setValue(TreeHollowBlock.SEALED, true)
                    .setValue(BirdNestBlock.OCCUPIED, withOwl));
            if (withOwl) settleOwl((WorldGenLevel) context.level(), context, pos);
            return;
        }
    }

    private void settleOwl(WorldGenLevel level, Context context, BlockPos pos) {
        RandomSource random = context.random();
        OwlType type = OwlType.forSpawn(level, pos, level.getBiome(pos), random);

        UUID owlId = new UUID(random.nextLong(), random.nextLong());
        context.level().getBlockEntity(pos, OwlMod.BIRD_NEST_TILE.get())
                .ifPresent(nest -> nest.settleFreshOwl(type, owlId));
        level.scheduleTick(pos, hollow, BirdNestBlockEntity.VISIT_TICKS);
    }

    private static List<BlockPos> trunkLogs(Context context) {
        List<BlockPos> logs = context.logs();
        if (logs.isEmpty()) return List.of();

        BlockPos root = logs.get(0);
        Set<BlockPos> allLogs = new HashSet<>(logs);

        List<BlockPos> trunk = new ArrayList<>();
        for (BlockPos pos : logs) {
            boolean sameColumn = pos.getX() == root.getX() && pos.getZ() == root.getZ();
            if (!sameColumn || !allLogs.contains(pos.above())) continue;
            int heightUpTheTrunk = logsBelow(allLogs, pos);
            if (heightUpTheTrunk < LOWEST_LOG || heightUpTheTrunk > HIGHEST_LOG) continue;
            trunk.add(pos);
        }
        return trunk;
    }

    private static int logsBelow(Set<BlockPos> allLogs, BlockPos pos) {
        int below = 0;
        BlockPos.MutableBlockPos cursor = pos.mutable().move(Direction.DOWN);
        while (below <= HIGHEST_LOG && allLogs.contains(cursor)) {
            below++;
            cursor.move(Direction.DOWN);
        }
        return below;
    }

    @Nullable
    private static Direction openFace(Context context, BlockPos pos, RandomSource random) {
        List<Direction> sides = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
        Util.shuffle(sides, random);
        for (Direction side : sides) {
            if (context.isAir(pos.relative(side))) return side;
        }
        return null;
    }
}
