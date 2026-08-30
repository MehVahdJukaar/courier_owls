package net.mehvahdjukaar.courier_owls;

import net.mehvahdjukaar.courier_owls.bird.brain.BirdSightSensor;
import net.mehvahdjukaar.courier_owls.bird.brain.BirdTemptationSensor;
import net.mehvahdjukaar.courier_owls.configs.ClientConfigs;
import net.mehvahdjukaar.courier_owls.configs.CommonConfigs;
import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.mehvahdjukaar.courier_owls.parcel.ParcelMod;
import net.mehvahdjukaar.courier_owls.parcel.SetPackageRecipientMessage;
import net.mehvahdjukaar.moonlight.api.misc.RegSupplier;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.platform.RegHelper;
import net.mehvahdjukaar.moonlight.api.platform.network.NetworkHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.SensorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class BirdMod {
    public static final String MOD_ID = "courier_owls";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier res(String name) {
        return Identifier.fromNamespaceAndPath(MOD_ID, name);
    }

    public static final RegSupplier<MemoryModuleType<BlockPos>> PERCH_POS = RegHelper.register(
            res("perch_pos"), () -> new MemoryModuleType<>(Optional.of(BlockPos.CODEC)),
            Registries.MEMORY_MODULE_TYPE);

    public static final RegSupplier<SensorType<BirdSightSensor>> BIRD_SIGHT = RegHelper.register(
            res("bird_sight"), () -> new SensorType<>(BirdSightSensor::new), Registries.SENSOR_TYPE);

    public static final RegSupplier<SensorType<BirdTemptationSensor>> BIRD_TEMPTATION_SENSOR = RegHelper.register(
            res("bird_temptation"), () -> new SensorType<>(BirdTemptationSensor::new), Registries.SENSOR_TYPE);

    public static void commonInit() {
        NetworkHelper.addNetworkRegistration(
                event -> event.registerServerBound(SetPackageRecipientMessage.TYPE), 1);

        CommonConfigs.init();
        if (PlatHelper.getPhysicalSide().isClient()) {
            ClientConfigs.init();
        }

        OwlMod.init();
        ParcelMod.init();
    }
}
