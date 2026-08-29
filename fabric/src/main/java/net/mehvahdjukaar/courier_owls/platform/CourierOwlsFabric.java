package net.mehvahdjukaar.courier_owls.platform;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.mehvahdjukaar.courier_owls.BirdMod;
import net.mehvahdjukaar.courier_owls.BirdModClient;
import net.mehvahdjukaar.courier_owls.ModEvents;
import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.mehvahdjukaar.courier_owls.owls.OwlSpawns;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.minecraft.world.entity.MobCategory;

public class CourierOwlsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        BirdMod.commonInit();

        if (PlatHelper.getPhysicalSide().isClient()) {
            BirdModClient.init();
        }

        ServerTickEvents.END_LEVEL_TICK.register(ModEvents::onServerLevelTick);

        for (OwlSpawns.Habitat habitat : OwlSpawns.HABITATS) {
            BiomeModifications.addSpawn(context -> OwlSpawns.habitatFor(context::hasTag) == habitat,
                    MobCategory.CREATURE, OwlMod.OWL.get(), habitat.weight(), 1, 1);
        }
    }
}
