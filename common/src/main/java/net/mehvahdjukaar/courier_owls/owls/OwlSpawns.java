package net.mehvahdjukaar.courier_owls.owls;

import net.mehvahdjukaar.courier_owls.BirdMod;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlEntity;
import net.mehvahdjukaar.moonlight.api.platform.RegHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public class OwlSpawns {
    public static final TagKey<Biome> DARK_FOREST = habitat("dark_forest");
    public static final TagKey<Biome> OLD_GROWTH_TAIGA = habitat("old_growth_taiga");
    public static final TagKey<Biome> SNOWY_TAIGA = habitat("snowy_taiga");
    public static final TagKey<Biome> TAIGA = habitat("taiga");
    public static final TagKey<Biome> SWAMP = habitat("swamp");

    public static final TagKey<Biome> RAINFOREST = habitat("rainforest");

    public static final TagKey<Biome> SPARSE_JUNGLE = habitat("sparse_jungle");
    public static final TagKey<Biome> SAVANNA = habitat("savanna");
    public static final TagKey<Biome> DESERT = habitat("desert");
    public static final TagKey<Biome> SNOWY_OPEN = habitat("snowy_open");
    public static final TagKey<Biome> ROCKY = habitat("rocky");
    public static final TagKey<Biome> RIVER = habitat("river");
    public static final TagKey<Biome> OPEN_FIELD = habitat("open_field");
    public static final TagKey<Biome> OAK_BIRCH_FOREST = habitat("oak_birch_forest");

    public static final TagKey<Biome> NO_OWLS = habitat("none");

    public static final List<Habitat> HABITATS = List.of(
            new Habitat(DARK_FOREST, 3),
            new Habitat(OLD_GROWTH_TAIGA, 3, "is_old_growth"),
            new Habitat(SNOWY_TAIGA, 2),
            new Habitat(TAIGA, 2, "is_taiga"),
            new Habitat(SWAMP, 2, "is_swamp"),
            new Habitat(RAINFOREST, 1, "is_jungle"),
            new Habitat(SPARSE_JUNGLE, 1),
            new Habitat(SAVANNA, 1, "is_savanna"),
            new Habitat(DESERT, 1, "is_desert"),
            new Habitat(SNOWY_OPEN, 1, "is_snowy", "is_icy"),
            new Habitat(ROCKY, 1, "is_mountain", "is_badlands", "is_windswept"),
            new Habitat(RIVER, 1, "is_river"),
            new Habitat(OPEN_FIELD, 1, "is_plains"),
            new Habitat(OAK_BIRCH_FOREST, 2, "is_forest"));

    public record Habitat(TagKey<Biome> biomes, int weight, List<TagKey<Biome>> resembles) {
        Habitat(TagKey<Biome> biomes, int weight, String... conventional) {
            this(biomes, weight, List.of(conventional).stream().map(Habitat::common).toList());
        }

        private static TagKey<Biome> common(String name) {
            return TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", name));
        }
    }

    @Nullable
    public static Habitat habitatFor(Predicate<TagKey<Biome>> isTagged) {
        for (Habitat habitat : HABITATS) {
            if (isTagged.test(habitat.biomes())) return habitat;
        }
        if (isTagged.test(NO_OWLS)) return null;
        for (Habitat habitat : HABITATS) {
            if (habitat.resembles().stream().anyMatch(isTagged)) return habitat;
        }
        return null;
    }

    public static void init() {
        RegHelper.addSpawnPlacementsRegistration(event -> event.register(OwlMod.OWL.get(),
                SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, OwlSpawns::canSpawnAt));
    }

    private static boolean canSpawnAt(EntityType<OwlEntity> type, LevelAccessor level, EntitySpawnReason spawnType,
                                      BlockPos pos, RandomSource random) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isValidSpawn(level, below, type);
    }

    private static TagKey<Biome> habitat(String name) {
        return TagKey.create(Registries.BIOME, BirdMod.res("owl_home/" + name));
    }
}
