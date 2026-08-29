package net.mehvahdjukaar.courier_owls.platform;

import com.mojang.serialization.MapCodec;
import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.mehvahdjukaar.courier_owls.owls.OwlSpawns;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

public record OwlSpawnModifier() implements BiomeModifier {
    public static final MapCodec<OwlSpawnModifier> CODEC = MapCodec.unit(OwlSpawnModifier::new);

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.ADD) return;
        OwlSpawns.Habitat habitat = OwlSpawns.habitatFor(biome::is);
        if (habitat == null) return;
        builder.getMobSpawnSettings().addSpawn(MobCategory.CREATURE, habitat.weight(),
                new MobSpawnSettings.SpawnerData(OwlMod.OWL.get(), 1, 1));
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return CODEC;
    }
}
