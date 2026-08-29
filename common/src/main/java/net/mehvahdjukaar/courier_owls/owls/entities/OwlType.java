package net.mehvahdjukaar.courier_owls.owls.entities;

import io.netty.buffer.ByteBuf;
import net.mehvahdjukaar.courier_owls.BirdMod;
import net.mehvahdjukaar.courier_owls.configs.CommonConfigs;
import net.mehvahdjukaar.courier_owls.owls.OwlSpawns;
import net.mehvahdjukaar.courier_owls.owls.particles.OwlFeather;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.biome.Biome;

import java.util.HashMap;
import java.util.Objects;
import java.util.Map;
import java.util.function.IntFunction;

public enum OwlType implements StringRepresentable {
    HORNED("horned", OwlFeather.HORNED),
    SNOW("snow", OwlFeather.SNOW),
    BARN("barn", OwlFeather.BARN),
    LITTLE("little", OwlFeather.LITTLE),
    BARRED("barred", OwlFeather.BARRED),
    FISHER("fisher", OwlFeather.FISHER),
    EAGLE("eagle", OwlFeather.EAGLE),
    MOON("moon", OwlFeather.MOON),
    DUO("duo", OwlFeather.DUO),

    SPECTACLED("spectacled", OwlFeather.SPECTACLED);

    public static final StringRepresentable.EnumCodec<OwlType> CODEC = StringRepresentable.fromEnum(OwlType::values);
    public static final StreamCodec<ByteBuf, OwlType> STREAM_CODEC = ByteBufCodecs.idMapper(OwlType::byId, Enum::ordinal);

    private static final String VARIANT_TAG = "Variant";
    private static final IntFunction<OwlType> BY_ID =
            ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.WRAP);

    public final String name;
    public final OwlFeather feather;
    public final Identifier texture;
    public final Identifier sleepingTexture;
    public final Identifier eyesTexture;

    OwlType(String name, OwlFeather feather) {
        this.name = name;
        this.feather = feather;
        this.texture = res("textures/entity/owl/" + name + ".png");
        this.sleepingTexture = res("textures/entity/owl/" + name + "_sleep.png");
        this.eyesTexture = res("textures/entity/owl/" + name + "_e.png");
    }

    public static final Identifier CHICK_TEXTURE = res("textures/entity/owl/owl_baby.png");
    public static final Identifier CHICK_SLEEPING_TEXTURE = res("textures/entity/owl/owl_baby_sleep.png");

    public static Identifier res(String name) {
        return BirdMod.res(name);
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public static OwlType byId(int id) {
        return BY_ID.apply(id);
    }

    public static void save(ValueOutput output, OwlType type) {
        output.store(VARIANT_TAG, CODEC, type);
    }

    public static OwlType load(ValueInput input) {
        return input.read(VARIANT_TAG, CODEC)
                .or(() -> input.getInt(VARIANT_TAG).map(OwlType::byId))
                .orElse(HORNED);
    }

    public static OwlType fromTag(CompoundTag tag) {
        return tag.getString(VARIANT_TAG).map(CODEC::byName).filter(Objects::nonNull)
                .or(() -> tag.getInt(VARIANT_TAG).map(OwlType::byId))
                .orElse(HORNED);
    }

    private static final String DUO_NAME = "duo";

    public static OwlType skinOf(OwlEntity owl) {
        if (owl.hasCustomName() && DUO_NAME.equalsIgnoreCase(owl.getName().getString())) return DUO;
        return  owl.getOwlType();
    }

    private static final float MOON_OWL_CHANCE = 0.025F;

    private static boolean isFullMoonNight(LevelAccessor level, BlockPos pos) {
        return level instanceof Level world && world.isDarkOutside()
                && level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, pos) == MoonPhase.FULL_MOON;
    }

    public static OwlType forSpawn(LevelAccessor level, BlockPos pos, Holder<Biome> biome, RandomSource rand) {
        if (!CommonConfigs.OWL_VARIANTS.get()) {
            return HORNED;
        }
        if (CommonConfigs.MOON_OWLS.get() && isFullMoonNight(level, pos)
                && rand.nextFloat() < MOON_OWL_CHANCE) return MOON;
        return getRandomFromBiome(biome, rand);
    }

    public static OwlType getRandomFromBiome(Holder<Biome> biome, RandomSource rand) {
        OwlSpawns.Habitat habitat = OwlSpawns.habitatFor(biome::is);
        if (habitat == null) return HORNED;
        return BY_HABITAT.getOrDefault(habitat.biomes(), ANYWHERE).getRandom(rand).orElse(HORNED);
    }

    private static final WeightedList<OwlType> ANYWHERE = WeightedList.of(HORNED);
    private static final Map<TagKey<Biome>, WeightedList<OwlType>> BY_HABITAT = new HashMap<>();

    private static void habitat(TagKey<Biome> biomes, Object... speciesThenWeight) {
        var builder = WeightedList.<OwlType>builder();
        for (int i = 0; i < speciesThenWeight.length; i += 2) {
            builder.add((OwlType) speciesThenWeight[i], (Integer) speciesThenWeight[i + 1]);
        }
        BY_HABITAT.put(biomes, builder.build());
    }

    static {
        habitat(OwlSpawns.DARK_FOREST, BARRED, 5, HORNED, 4, LITTLE, 1);
        habitat(OwlSpawns.OLD_GROWTH_TAIGA, HORNED, 3, BARRED, 3, FISHER, 2, SNOW, 1);
        habitat(OwlSpawns.OAK_BIRCH_FOREST, LITTLE, 4, HORNED, 3, BARRED, 3, BARN, 2);
        habitat(OwlSpawns.TAIGA, HORNED, 4, BARRED, 2, SNOW, 1);
        habitat(OwlSpawns.SNOWY_TAIGA, SNOW, 4, HORNED, 2);
        habitat(OwlSpawns.SWAMP, BARRED, 5, BARN, 2, HORNED, 2, FISHER, 1);
        habitat(OwlSpawns.OPEN_FIELD, BARN, 5, LITTLE, 3, HORNED, 2);
        habitat(OwlSpawns.SAVANNA, BARN, 4, HORNED, 3, LITTLE, 2, SPECTACLED, 1);
        habitat(OwlSpawns.SNOWY_OPEN, SNOW, 6, HORNED, 1, EAGLE, 1);
        habitat(OwlSpawns.ROCKY, EAGLE, 4, HORNED, 2);
        habitat(OwlSpawns.RAINFOREST, SPECTACLED, 6, HORNED, 1);
        habitat(OwlSpawns.SPARSE_JUNGLE, SPECTACLED, 3, HORNED, 2, BARN, 1);
        habitat(OwlSpawns.DESERT, HORNED, 3, BARN, 2);
        habitat(OwlSpawns.RIVER, FISHER, 3, BARN, 1);
    }
}
