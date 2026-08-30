package net.mehvahdjukaar.courier_owls.parcel;

import net.mehvahdjukaar.courier_owls.parcel.client.CardboardPackageScreen;
import net.mehvahdjukaar.moonlight.api.platform.ClientHelper;

public class ParcelModClient {
    public static void init() {
        ClientHelper.addMenuScreensRegistration(event ->
                event.register(ParcelMod.CARDBOARD_PACKAGE_MENU.get(), CardboardPackageScreen::new));
    }
}
