package net.mehvahdjukaar.courier_owls.owls;

import io.netty.buffer.ByteBuf;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

public record ShoulderRidingOwls(@Nullable OwlType left, @Nullable OwlType right) {
    public static final ShoulderRidingOwls NONE = new ShoulderRidingOwls(null, null);

    public static final StreamCodec<ByteBuf, ShoulderRidingOwls> STREAM_CODEC = StreamCodec.of(
            (buf, riders) -> {
                ByteBufCodecs.VAR_INT.encode(buf, slotId(riders.left));
                ByteBufCodecs.VAR_INT.encode(buf, slotId(riders.right));
            },
            buf -> new ShoulderRidingOwls(slotType(ByteBufCodecs.VAR_INT.decode(buf)),
                    slotType(ByteBufCodecs.VAR_INT.decode(buf))));

    private static int slotId(@Nullable OwlType type) {
        return type == null ? 0 : type.ordinal() + 1;
    }

    private static @Nullable OwlType slotType(int id) {
        return id == 0 ? null : OwlType.byId(id - 1);
    }

    public @Nullable OwlType on(boolean left) {
        return left ? this.left : this.right;
    }

    public ShoulderRidingOwls with(boolean left, @Nullable OwlType type) {
        return left ? new ShoulderRidingOwls(type, this.right) : new ShoulderRidingOwls(this.left, type);
    }
}
