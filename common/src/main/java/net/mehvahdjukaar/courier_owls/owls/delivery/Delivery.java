package net.mehvahdjukaar.courier_owls.owls.delivery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.StringRepresentable;

import java.util.Optional;
import java.util.UUID;

public record Delivery(Leg leg, Optional<UUID> addressee, long legEndsAt,
                       boolean triedHollow, boolean puffed, Optional<BlockPos> exit) {
    public enum Leg implements StringRepresentable {
        ATTEND_CONFIRM("confirm"),
        OUTBOUND("outbound"),
        ATTEND_DELIVERING("delivering"),
        RETURNING("returning"),
        ATTEND_HOME("home");

        public static final Codec<Leg> CODEC = StringRepresentable.fromEnum(Leg::values);

        private final String name;

        Leg(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public static final Codec<Delivery> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Leg.CODEC.fieldOf("leg").forGetter(Delivery::leg),
            UUIDUtil.CODEC.optionalFieldOf("addressee").forGetter(Delivery::addressee),
            Codec.LONG.fieldOf("leg_ends_at").forGetter(Delivery::legEndsAt),

            Codec.BOOL.optionalFieldOf("tried_hollow", false).forGetter(Delivery::triedHollow),

            Codec.BOOL.optionalFieldOf("puffed", false).forGetter(Delivery::puffed),
            BlockPos.CODEC.optionalFieldOf("exit").forGetter(Delivery::exit)
    ).apply(instance, Delivery::new));

    public static Delivery toHollow(long confirmEndsAt) {
        return new Delivery(Leg.ATTEND_CONFIRM, Optional.empty(), confirmEndsAt, false, false, Optional.empty());
    }

    public static Delivery toPlayer(UUID addressee, long confirmEndsAt) {
        return new Delivery(Leg.ATTEND_CONFIRM, Optional.of(addressee), confirmEndsAt,
                false, false, Optional.empty());
    }

    public Delivery on(Leg leg, long endsAt) {
        return new Delivery(leg, this.addressee, endsAt, this.triedHollow, false, Optional.empty());
    }

    public Delivery divertedHome(long endsAt) {
        return new Delivery(Leg.OUTBOUND, Optional.empty(), endsAt, this.triedHollow,
                false, Optional.empty());
    }

    public Delivery afterTryingHollow() {
        return new Delivery(this.leg, this.addressee, this.legEndsAt, true, this.puffed, this.exit);
    }

    public Delivery puffedUntil(long endsAt) {
        return new Delivery(this.leg, this.addressee, endsAt, this.triedHollow, true, Optional.empty());
    }

    public Delivery leavingFrom(BlockPos exit, long endsAt) {
        return new Delivery(this.leg, this.addressee, endsAt, this.triedHollow, this.puffed, Optional.of(exit));
    }

    public boolean legOver(long gameTime) {
        return gameTime >= this.legEndsAt;
    }
}
