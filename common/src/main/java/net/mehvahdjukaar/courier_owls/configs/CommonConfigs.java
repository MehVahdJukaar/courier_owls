package net.mehvahdjukaar.courier_owls.configs;

import net.mehvahdjukaar.courier_owls.BirdMod;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigBuilder;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigType;
import net.mehvahdjukaar.moonlight.api.platform.configs.ModConfigHolder;

import java.util.function.Supplier;

public class CommonConfigs {
    public static final Supplier<Boolean> OWL_VARIANTS;
    public static final Supplier<Boolean> MOON_OWLS;
    public static final Supplier<Boolean> CHICKS_STAY_GROUNDED;
    public static final Supplier<Boolean> STRANDED_PARCEL_GOES_HOME;
    public static final Supplier<Boolean> ANNOUNCE_DELIVERIES;
    public static final Supplier<Double> TREE_HOLLOW_CHANCE;
    public static final Supplier<Double> HOLLOW_OWL_CHANCE;
    public static final ModConfigHolder SPEC;

    static {
        ConfigBuilder builder = ConfigBuilder.create(BirdMod.MOD_ID, ConfigType.COMMON);

        builder.push("spawning");
        OWL_VARIANTS = builder.comment("Whether owls come in species. Off, every owl that spawns is a great horned owl")
                .define("owl_variants", true);
        MOON_OWLS = builder.comment("Whether moon owls spawn at night under a full moon")
                .define("moon_owls", false);
        builder.pop();

        builder.push("chicks");
        CHICKS_STAY_GROUNDED = builder.comment("Chicks cannot fly: they walk everywhere until they grow up")
                .define("chicks_stay_grounded", true);
        builder.pop();

        builder.push("delivery");
        STRANDED_PARCEL_GOES_HOME = builder.comment("If the recipient logs out, the owl takes the parcel to its hollow instead of waiting")
                .define("stranded_parcel_goes_home", false);
        ANNOUNCE_DELIVERIES = builder.comment("Tell the recipient when an owl sets off and when it arrives")
                .define("announce_deliveries", true);
        builder.pop();

        builder.push("worldgen");
        TREE_HOLLOW_CHANCE = builder.comment("Chance for a tree to grow with a hollow in its trunk. Needs a rejoin")
                .defineSlider("tree_hollow_chance", 0.0025, 0.0, 1.0);
        HOLLOW_OWL_CHANCE = builder.comment("Chance for a generated hollow to already have an owl in it")
                .defineSlider("hollow_owl_chance", 0.5, 0.0, 1.0);
        builder.pop();

        SPEC = builder.build();
        SPEC.forceLoad();
    }

    public static void init() {
    }
}
