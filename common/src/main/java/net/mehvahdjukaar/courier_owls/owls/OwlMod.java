package net.mehvahdjukaar.courier_owls.owls;

import net.mehvahdjukaar.courier_owls.BirdMod;
import net.mehvahdjukaar.courier_owls.owls.blocks.BirdHouseBlock;
import net.mehvahdjukaar.courier_owls.owls.blocks.BirdNestBlockEntity;
import net.mehvahdjukaar.courier_owls.owls.blocks.BirdNestMenu;
import net.mehvahdjukaar.courier_owls.owls.blocks.OwlBlocksClientPack;
import net.mehvahdjukaar.courier_owls.owls.blocks.OwlBlocksServerPack;
import net.mehvahdjukaar.courier_owls.owls.blocks.TreeHollowBlock;
import net.mehvahdjukaar.courier_owls.owls.worldgen.TreeHollowDecorator;
import net.mehvahdjukaar.courier_owls.owls.worldgen.TreeHollowInjection;
import net.mehvahdjukaar.courier_owls.owls.delivery.DeliveringOwlsStorage;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlEntity;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlPreySensor;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlType;
import net.mehvahdjukaar.courier_owls.owls.particles.OwlFeatherOptions;
import net.mehvahdjukaar.moonlight.api.item.WoodBasedBlockItem;
import net.mehvahdjukaar.moonlight.api.misc.RegSupplier;
import net.mehvahdjukaar.moonlight.api.misc.Registrator;
import net.mehvahdjukaar.moonlight.api.misc.WorldSavedDataType;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.platform.RegHelper;
import net.minecraft.util.Unit;
import net.mehvahdjukaar.moonlight.api.set.BlockSetAPI;
import net.mehvahdjukaar.moonlight.api.set.wood.WoodType;
import net.mehvahdjukaar.moonlight.api.set.wood.WoodTypeRegistry;
import net.mehvahdjukaar.moonlight.api.util.Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleType;
import net.mehvahdjukaar.moonlight.api.misc.IAttachmentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.entity.EntityType;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class OwlMod {
    public static final String OWL_NAME = "owl";

    public static final Supplier<EntityDataSerializer<OwlType>> OWL_TYPE_DATA = RegHelper.registerEntityDataSerializer(
            BirdMod.res("owl_type"), () -> EntityDataSerializer.forValueType(OwlType.STREAM_CODEC));

    public static final RegSupplier<EntityType<OwlEntity>> OWL = RegHelper.registerEntityType(
            BirdMod.res(OWL_NAME),

            EntityType.Builder.of(OwlEntity::new, MobCategory.CREATURE)
                    .clientTrackingRange(64)
                    .updateInterval(3)

                    .sized(0.5f, 0.75f));

    // temp sounds
    public static final RegSupplier<SoundEvent> OWL_AMBIENT = RegHelper.registerSound(BirdMod.res("entity.owl.ambient"));
    public static final RegSupplier<SoundEvent> OWL_HURT = RegHelper.registerSound(BirdMod.res("entity.owl.hurt"));
    public static final RegSupplier<SoundEvent> OWL_DEATH = RegHelper.registerSound(BirdMod.res("entity.owl.death"));
    public static final RegSupplier<SoundEvent> OWL_FLAP = RegHelper.registerSound(BirdMod.res("entity.owl.flap"));

    public static final TagKey<EntityType<?>> OWL_PREY = TagKey.create(Registries.ENTITY_TYPE, BirdMod.res("owl_prey"));
    public static final TagKey<EntityType<?>> OWL_TAME_PREY = TagKey.create(Registries.ENTITY_TYPE, BirdMod.res("owl_tame_prey"));
    public static final TagKey<Item> OWL_FOOD = TagKey.create(Registries.ITEM, BirdMod.res("owl_food"));

    public static final RegSupplier<SensorType<OwlPreySensor>> OWL_PREY_SENSOR = RegHelper.register(
            BirdMod.res("owl_prey"), () -> new SensorType<>(OwlPreySensor::new), Registries.SENSOR_TYPE);

    public static final RegSupplier<MemoryModuleType<Unit>> SWOOP_GO = RegHelper.register(
            BirdMod.res("swoop_go"), () -> new MemoryModuleType<>(Optional.empty()),
            Registries.MEMORY_MODULE_TYPE);

    public static final RegSupplier<MemoryModuleType<Player>> ATTENDED_PLAYER = RegHelper.register(
            BirdMod.res("attended_player"), () -> new MemoryModuleType<>(Optional.empty()),
            Registries.MEMORY_MODULE_TYPE);

    public static final RegSupplier<MemoryModuleType<BlockPos>> NEST_POS = RegHelper.register(
            BirdMod.res("nest_pos"), () -> new MemoryModuleType<>(Optional.of(BlockPos.CODEC)),
            Registries.MEMORY_MODULE_TYPE);

    public static final RegSupplier<MemoryModuleType<Unit>> VISIT_NEST = RegHelper.register(
            BirdMod.res("visit_nest"), () -> new MemoryModuleType<>(Optional.empty()),
            Registries.MEMORY_MODULE_TYPE);

    public static final RegSupplier<MemoryModuleType<BlockPos>> ROOST_POS = RegHelper.register(
            BirdMod.res("roost_pos"), () -> new MemoryModuleType<>(Optional.of(BlockPos.CODEC)),
            Registries.MEMORY_MODULE_TYPE);

    public static final RegSupplier<Activity> DELIVERY = RegHelper.register(
            BirdMod.res("delivery"), () -> new Activity("delivery"), Registries.ACTIVITY);

    public static final WorldSavedDataType<DeliveringOwlsStorage> PUFFED_OWLS = RegHelper.registerWorldSavedData(
            BirdMod.res("puffed_owls"), DeliveringOwlsStorage::new, () -> DeliveringOwlsStorage.CODEC, null, true);

    public static final Supplier<ParticleType<OwlFeatherOptions>> OWL_FEATHER_PARTICLE =
            RegHelper.registerParticle(BirdMod.res("owl_feather"),
                    OwlFeatherOptions.CODEC, OwlFeatherOptions.STREAM_CODEC);

    public static final Supplier<Item> OWL_SPAWN_EGG_ITEM = RegHelper.registerItem(
            BirdMod.res(OWL_NAME + "_spawn_egg"),
            p -> PlatHelper.newSpawnEgg(OWL, 0x32211a, 0xa4935d, p));

    public static final String BIRD_HOUSE_NAME = "bird_house";
    public static final String TREE_HOLLOW_NAME = "tree_hollow";

    public static final Map<WoodType, Block> BIRD_HOUSES = new LinkedHashMap<>();
    public static final Map<WoodType, Block> TREE_HOLLOWS = new LinkedHashMap<>();

    public static final RegSupplier<PoiType> TREE_HOLLOW_POI = RegHelper.registerPOI(
            BirdMod.res(TREE_HOLLOW_NAME), () -> new PoiType(Set.of(), 1, 1));

    public static final RegSupplier<PoiType> BIRD_HOUSE_POI = RegHelper.registerPOI(
            BirdMod.res(BIRD_HOUSE_NAME), () -> new PoiType(Set.of(), 1, 1));

    public static final RegSupplier<TreeDecoratorType<TreeHollowDecorator>> TREE_HOLLOW_DECORATOR =
            RegHelper.register(BirdMod.res(TREE_HOLLOW_NAME),
                    () -> new TreeDecoratorType<>(TreeHollowDecorator.CODEC), Registries.TREE_DECORATOR_TYPE);

    public static final RegSupplier<BlockEntityType<BirdNestBlockEntity>> BIRD_NEST_TILE =
            RegHelper.registerBlockEntityType(BirdMod.res("bird_nest"),

                    () -> PlatHelper.newBlockEntityType(BirdNestBlockEntity::new,
                            nestBlocks().toArray(Block[]::new)));

    public static final IAttachmentType<ShoulderRidingOwls, Player> SHOULDER_OWLS =
            RegHelper.registerDataAttachment(BirdMod.res("shoulder_owls"),
                    () -> RegHelper.AttachmentBuilder.create(() -> ShoulderRidingOwls.NONE)
                            .syncWith(ShoulderRidingOwls.STREAM_CODEC),
                    Player.class);

    public static final RegSupplier<MenuType<BirdNestMenu>> BIRD_NEST_MENU = RegHelper.registerSimpleMenuType(
            BirdMod.res("bird_nest"), BirdNestMenu::new);

    public static void init() {
        BlockSetAPI.addDynamicRegistration(BirdMod.MOD_ID, OwlMod::registerBlocks, BuiltInRegistries.BLOCK);
        BlockSetAPI.addDynamicRegistration(BirdMod.MOD_ID, OwlMod::registerItems, BuiltInRegistries.ITEM);

        TreeHollowInjection.init();
        OwlSpawns.init();

        RegHelper.registerDynamicResourceProvider(new OwlBlocksServerPack());
        if (PlatHelper.getPhysicalSide().isClient()) {
            RegHelper.registerDynamicResourceProvider(new OwlBlocksClientPack());
        }

        RegHelper.addExtraBEBlockStatesRegistration(event -> event.addBlocks(BIRD_NEST_TILE.get(),
                nestBlocks().toArray(Block[]::new)));
        RegHelper.addExtraPOIStatesRegistration(event -> {
            event.addBlocks(TREE_HOLLOW_POI.getKey(), TREE_HOLLOWS.values());
            event.addBlocks(BIRD_HOUSE_POI.getKey(), BIRD_HOUSES.values());
        });

        RegHelper.addAttributeRegistration(event -> {
            event.register(OWL.get(), OwlEntity.makeAttributes());
        });
        RegHelper.addItemsToTabsRegistration(event -> {
            event.add(CreativeModeTabs.SPAWN_EGGS, OWL_SPAWN_EGG_ITEM.get());
            BIRD_HOUSES.values().forEach(b -> event.add(CreativeModeTabs.FUNCTIONAL_BLOCKS, b));
            TREE_HOLLOWS.values().forEach(b -> event.add(CreativeModeTabs.FUNCTIONAL_BLOCKS, b));
        });
    }

    private static Stream<Block> nestBlocks() {
        return Stream.concat(BIRD_HOUSES.values().stream(), TREE_HOLLOWS.values().stream());
    }

    private static void registerBlocks(Registrator<Block> event) {
        for (WoodType wood : WoodTypeRegistry.INSTANCE.getValues()) {
            Identifier houseId = BirdMod.res(wood.getVariantId(BIRD_HOUSE_NAME));
            BirdHouseBlock birdHouse = new BirdHouseBlock(wood, blockProperties(houseId, wood)
                    .strength(2.0F, 3.0F)
                    .noOcclusion());
            event.register(houseId, birdHouse);
            wood.addChild(BirdMod.MOD_ID + ":" + BIRD_HOUSE_NAME, birdHouse);
            BIRD_HOUSES.put(wood, birdHouse);

            if (wood.isBambooLike() || !wood.canBurn()) continue;
            Identifier hollowId = BirdMod.res(wood.getVariantId(TREE_HOLLOW_NAME));
            TreeHollowBlock treeHollow = new TreeHollowBlock(wood, blockProperties(hollowId, wood)
                    .strength(2.0F));
            event.register(hollowId, treeHollow);
            wood.addChild(BirdMod.MOD_ID + ":" + TREE_HOLLOW_NAME, treeHollow);
            TREE_HOLLOWS.put(wood, treeHollow);
        }
    }

    private static BlockBehaviour.Properties blockProperties(Identifier id, WoodType wood) {
        return wood.copyProperties()
                .instrument(NoteBlockInstrument.BASS)
                .setId(ResourceKey.create(Registries.BLOCK, id));
    }

    private static void registerItems(Registrator<Item> event) {
        registerItems(event, BIRD_HOUSES);
        registerItems(event, TREE_HOLLOWS);
    }

    private static void registerItems(Registrator<Item> event, Map<WoodType, Block> blocks) {
        blocks.forEach((wood, block) ->
                event.registerItem(Utils.getID(block), p -> new WoodBasedBlockItem(block, p, wood)));
    }
}
