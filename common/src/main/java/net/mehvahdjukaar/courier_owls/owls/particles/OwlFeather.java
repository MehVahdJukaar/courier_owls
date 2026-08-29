package net.mehvahdjukaar.courier_owls.owls.particles;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;

import java.util.function.IntFunction;

public enum OwlFeather implements StringRepresentable {
    HORNED("horned", 43),
    SNOW("snow", 43),
    BARN("barn", 43),
    LITTLE("little", 43),
    BARRED("barred", 43),
    FISHER("fisher", 43),
    EAGLE("eagle", 43),
    MOON("moon", 43),
    DUO("duo", 43),
    SPECTACLED("spectacled", 43),
    BABY("baby", 43);

    public static final Codec<OwlFeather> CODEC = StringRepresentable.fromEnum(OwlFeather::values);
    public static final StreamCodec<ByteBuf, OwlFeather> STREAM_CODEC =
            ByteBufCodecs.idMapper(OwlFeather::byId, Enum::ordinal);

    private static final IntFunction<OwlFeather> BY_ID =
            ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.WRAP);

    private final String name;

    public final float tilt;

    OwlFeather(String name, float tiltDegrees) {
        this.name = name;
        this.tilt = tiltDegrees * Mth.DEG_TO_RAD;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public static OwlFeather byId(int id) {
        return BY_ID.apply(id);
    }
}
