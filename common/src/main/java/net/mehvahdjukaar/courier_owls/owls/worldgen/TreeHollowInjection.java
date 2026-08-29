package net.mehvahdjukaar.courier_owls.owls.worldgen;

import net.mehvahdjukaar.courier_owls.configs.CommonConfigs;
import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.set.BlockSetAPI;
import net.mehvahdjukaar.moonlight.api.set.wood.WoodType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TreeHollowInjection {
    public static void init() {
        PlatHelper.addReloadableCommonSetup((registries, isClientSide) -> {
            if (!isClientSide) inject(registries);
        });
    }

    private static void inject(RegistryAccess registries) {
        float chance = CommonConfigs.TREE_HOLLOW_CHANCE.get().floatValue();
        if (chance <= 0) return;
        float owlChance = CommonConfigs.HOLLOW_OWL_CHANCE.get().floatValue();

        RandomSource sampler = RandomSource.create(0);
        for (ConfiguredFeature<?, ?> feature : registries.lookupOrThrow(Registries.CONFIGURED_FEATURE)) {
            if (!(feature.config() instanceof TreeConfiguration tree)) continue;
            if (tree.decorators.stream().anyMatch(d -> d instanceof TreeHollowDecorator)) continue;

            Block trunk;
            try {
                trunk = tree.trunkProvider.getState(null, sampler, BlockPos.ZERO).getBlock();
            } catch (Exception ignored) {
                continue;
            }
            Block hollow = hollowFor(trunk);
            if (hollow == null) continue;
            List<TreeDecorator> grown = new ArrayList<>(tree.decorators);
            grown.add(new TreeHollowDecorator(hollow, chance, owlChance));
            tree.decorators = List.copyOf(grown);
        }
    }

    @Nullable
    private static Block hollowFor(Block log) {
        WoodType wood = BlockSetAPI.getBlockTypeOf(log, WoodType.class);
        return wood == null ? null : OwlMod.TREE_HOLLOWS.get(wood);
    }
}
