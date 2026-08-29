package net.mehvahdjukaar.courier_owls.owls.particles;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record OwlFeatherOptions(OwlFeather feather) implements ParticleOptions {
    public static final MapCodec<OwlFeatherOptions> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            OwlFeather.CODEC.fieldOf("feather").forGetter(OwlFeatherOptions::feather)
    ).apply(i, OwlFeatherOptions::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, OwlFeatherOptions> STREAM_CODEC =
            StreamCodec.composite(OwlFeather.STREAM_CODEC, OwlFeatherOptions::feather, OwlFeatherOptions::new);

    @Override
    public ParticleType<?> getType() {
        return OwlMod.OWL_FEATHER_PARTICLE.get();
    }
}
