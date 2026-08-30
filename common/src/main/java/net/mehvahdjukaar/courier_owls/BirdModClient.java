package net.mehvahdjukaar.courier_owls;

import net.mehvahdjukaar.courier_owls.owls.OwlModClient;
import net.mehvahdjukaar.courier_owls.parcel.ParcelModClient;

public class BirdModClient {
    public static void init() {
        OwlModClient.init();
        ParcelModClient.init();
    }
}
