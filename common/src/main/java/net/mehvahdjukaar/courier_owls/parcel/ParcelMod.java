package net.mehvahdjukaar.courier_owls.parcel;

import com.mojang.serialization.Codec;
import net.mehvahdjukaar.courier_owls.BirdMod;
import net.mehvahdjukaar.moonlight.api.misc.RegSupplier;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.platform.RegHelper;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Supplier;

public class ParcelMod {
    public static final String CARDBOARD_PACKAGE_NAME = "cardboard_package";

    public static final Supplier<DataComponentType<String>> RECIPIENT = RegHelper.registerDataComponent(
            BirdMod.res("recipient"),
            () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    public static final RegSupplier<Block> CARDBOARD_PACKAGE = RegHelper.registerBlock(
            BirdMod.res(CARDBOARD_PACKAGE_NAME),
            CardboardPackageBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(0.4F)
                    .sound(SoundType.WOOL)
                    .noOcclusion());

    public static final Supplier<Item> CARDBOARD_PACKAGE_ITEM = RegHelper.registerItem(
            BirdMod.res(CARDBOARD_PACKAGE_NAME),
            p -> new CardboardPackageItem(CARDBOARD_PACKAGE.get(), p),
            new Item.Properties().stacksTo(1).useBlockDescriptionPrefix());

    public static final RegSupplier<BlockEntityType<CardboardPackageBlockEntity>> CARDBOARD_PACKAGE_TILE =
            RegHelper.registerBlockEntityType(BirdMod.res(CARDBOARD_PACKAGE_NAME),
                    CardboardPackageBlockEntity::new, CARDBOARD_PACKAGE);

    public static final RegSupplier<MenuType<CardboardPackageMenu>> CARDBOARD_PACKAGE_MENU =
            RegHelper.registerMenuType(BirdMod.res(CARDBOARD_PACKAGE_NAME), CardboardPackageMenu::new);

    public static void init() {
        if (isRedundant()) return;

        RegHelper.registerDynamicResourceProvider(new CardboardRecipePack());
        RegHelper.addItemsToTabsRegistration(event ->
                event.add(CreativeModeTabs.FUNCTIONAL_BLOCKS, CARDBOARD_PACKAGE_ITEM.get()));
    }

    public static boolean isRedundant() {
        return PlatHelper.isModLoaded("supplementaries");
    }
}
