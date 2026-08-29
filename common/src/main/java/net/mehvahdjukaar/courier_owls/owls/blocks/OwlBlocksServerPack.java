package net.mehvahdjukaar.courier_owls.owls.blocks;

import net.mehvahdjukaar.courier_owls.BirdMod;
import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.mehvahdjukaar.moonlight.api.resources.ResType;
import net.mehvahdjukaar.moonlight.api.resources.SimpleTagBuilder;
import net.mehvahdjukaar.moonlight.api.resources.StaticResource;
import net.mehvahdjukaar.moonlight.api.resources.pack.DynamicServerResourceProvider;
import net.mehvahdjukaar.moonlight.api.resources.pack.PackGenerationStrategy;
import net.mehvahdjukaar.moonlight.api.resources.pack.ResourceGenTask;
import net.mehvahdjukaar.moonlight.api.resources.pack.ResourceSink;
import net.mehvahdjukaar.moonlight.api.set.wood.VanillaWoodTypes;
import net.mehvahdjukaar.moonlight.api.set.wood.WoodType;
import net.mehvahdjukaar.moonlight.api.util.Utils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class OwlBlocksServerPack extends DynamicServerResourceProvider {
    public OwlBlocksServerPack() {
        super(BirdMod.res("dynamic_assets"), PackGenerationStrategy.REGEN_ON_EVERY_RELOAD);
    }

    @Override
    protected Collection<String> gatherSupportedNamespaces() {
        return List.of("minecraft");
    }

    @Override
    public void regenerateDynamicAssets(Consumer<ResourceGenTask> executor) {
        executor.accept((manager, sink) -> {
            SimpleTagBuilder axeMineable = SimpleTagBuilder.of(Identifier.withDefaultNamespace("mineable/axe"));

            Block oakHollow = OwlMod.TREE_HOLLOWS.get(VanillaWoodTypes.OAK);
            StaticResource oakHollowLoot = StaticResource.getOrLog(manager,
                    ResType.LOOT_TABLES.getPath(oakHollow.getLootTable().orElseThrow().identifier()));

            addSet(manager, sink, axeMineable, OwlMod.BIRD_HOUSES, OwlMod.BIRD_HOUSE_NAME, "bird_houses",
                    (wood, block) -> sink.addSimpleBlockLootTable(block));
            addSet(manager, sink, axeMineable, OwlMod.TREE_HOLLOWS, OwlMod.TREE_HOLLOW_NAME, "tree_hollows",
                    (wood, block) -> addHollowLootTable(manager, sink, oakHollowLoot, oakHollow, wood, block));

            sink.addTag(axeMineable, Registries.BLOCK);
        });
    }

    private static void addHollowLootTable(ResourceManager manager, ResourceSink sink,
                                           @Nullable StaticResource oakTable, Block oakHollow,
                                           WoodType wood, Block hollow) {
        if (oakTable == null || hollow == oakHollow) return;
        String fileName = Utils.getID(hollow).getPath() + ".json";
        Function<String, String> retarget = s -> s
                .replace(Utils.getID(oakHollow).toString(), Utils.getID(hollow).toString())
                .replace(Utils.getID(VanillaWoodTypes.OAK.log).toString(), Utils.getID(wood.log).toString());
        try {
            sink.addSimilarJsonResource(manager, oakTable, retarget, s -> fileName);
        } catch (Exception e) {
            BirdMod.LOGGER.error("Failed to generate tree hollow loot table for {}:", wood, e);
        }
    }

    private static void addSet(ResourceManager manager, ResourceSink sink, SimpleTagBuilder axeMineable,
                               Map<WoodType, Block> blocks, String baseName, String tagName,
                               BiConsumer<WoodType, Block> lootTable) {
        SimpleTagBuilder tag = SimpleTagBuilder.of(BirdMod.res(tagName));
        Identifier oakRecipe = BirdMod.res(baseName + "_oak");

        blocks.forEach((wood, block) -> {
            tag.addEntry(block);
            axeMineable.addEntry(block);
            lootTable.accept(wood, block);

            if (wood != VanillaWoodTypes.OAK) {
                try {
                    sink.addBlockTypeSwapRecipe(manager, oakRecipe, VanillaWoodTypes.OAK, wood, Utils.getID(block));
                } catch (Exception e) {
                    BirdMod.LOGGER.error("Failed to generate {} recipe for {}:", baseName, wood, e);
                }
            }
        });

        sink.addTag(tag, Registries.BLOCK);
        sink.addTag(tag, Registries.ITEM);
    }
}
