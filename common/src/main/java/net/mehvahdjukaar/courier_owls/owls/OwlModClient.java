package net.mehvahdjukaar.courier_owls.owls;

import net.mehvahdjukaar.courier_owls.BirdMod;
import net.mehvahdjukaar.courier_owls.owls.client.ClientSetup;
import net.mehvahdjukaar.courier_owls.owls.client.block_models.NestBlockModel;
import net.mehvahdjukaar.courier_owls.owls.client.gui.BirdNestScreen;
import net.mehvahdjukaar.courier_owls.owls.client.gui.OwlShowcaseWidget;
import net.mehvahdjukaar.moonlight.api.client.gui.ConfigScreenExtensions;
import net.mehvahdjukaar.moonlight.api.platform.ClientHelper;

public class OwlModClient {
    private static final String NEST_MODEL = "nest";

    public static void init() {
        ClientSetup.init();

        ConfigScreenExtensions.registerShowcase(BirdMod.MOD_ID,
                (modId, x, y, width, maxHeight) -> new OwlShowcaseWidget(x, y, width, maxHeight));
        ClientHelper.addMenuScreensRegistration(event ->
                event.register(OwlMod.BIRD_NEST_MENU.get(), BirdNestScreen::new));
        ClientHelper.addBlockModelRegistration(event ->
                event.register(BirdMod.res(NEST_MODEL), NestBlockModel.Unbaked.CODEC));
    }
}
