package net.mehvahdjukaar.courier_owls;

import net.mehvahdjukaar.courier_owls.owls.ShoulderOwlSync;
import net.mehvahdjukaar.courier_owls.owls.delivery.DeliveringOwlsStorage;
import net.mehvahdjukaar.moonlight.api.misc.EventCalled;
import net.minecraft.server.level.ServerLevel;

public class ModEvents {
    @EventCalled
    public static void onServerLevelTick(ServerLevel level) {
        DeliveringOwlsStorage.of(level).tick(level);
        ShoulderOwlSync.tick(level);
    }
}
