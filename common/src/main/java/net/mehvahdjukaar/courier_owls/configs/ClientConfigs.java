package net.mehvahdjukaar.courier_owls.configs;

import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigBuilder;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigType;
import net.mehvahdjukaar.moonlight.api.platform.configs.ModConfigHolder;
import net.mehvahdjukaar.courier_owls.BirdMod;

import java.util.function.Supplier;

public class ClientConfigs {
    public static final Supplier<Boolean> TWO_PIECE_WINGS;
    public static final ModConfigHolder SPEC;

    static {
        ConfigBuilder builder = ConfigBuilder.create(BirdMod.MOD_ID, ConfigType.CLIENT);

        builder.push("model");
        TWO_PIECE_WINGS = builder.comment("Bend the owl's wing at the wrist. More movement, but a seam shows from some angles")
                .define("two_piece_wings", false);
        builder.pop();

        SPEC = builder.build();
        SPEC.forceLoad();
    }

    public static void init() {
    }
}
