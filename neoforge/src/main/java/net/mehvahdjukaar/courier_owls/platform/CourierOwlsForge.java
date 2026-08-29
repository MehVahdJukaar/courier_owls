package net.mehvahdjukaar.courier_owls.platform;

import net.mehvahdjukaar.courier_owls.BirdMod;
import net.mehvahdjukaar.courier_owls.BirdModClient;
import net.mehvahdjukaar.courier_owls.ModEvents;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.platform.RegHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@Mod(BirdMod.MOD_ID)
public class CourierOwlsForge {
    public CourierOwlsForge(IEventBus bus) {
        BirdMod.commonInit();

        if (PlatHelper.getPhysicalSide().isClient()) {
            BirdModClient.init();
        }

        RegHelper.register(BirdMod.res("owl_spawns"), () -> OwlSpawnModifier.CODEC,
                NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS);

        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ModEvents.onServerLevelTick(level);
        }
    }
}
