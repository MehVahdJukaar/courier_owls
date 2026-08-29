package net.mehvahdjukaar.courier_owls.owls;

import net.mehvahdjukaar.courier_owls.owls.entities.OwlType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

public class ShoulderOwlSync {
    public static void tick(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            ShoulderRidingOwls riding = new ShoulderRidingOwls(
                    shoulderOwl(player.getShoulderEntityLeft()),
                    shoulderOwl(player.getShoulderEntityRight()));
            if (riding.equals(OwlMod.SHOULDER_OWLS.getOrCreate(player))) continue;
            OwlMod.SHOULDER_OWLS.set(player, riding);
            OwlMod.SHOULDER_OWLS.sync(player);
        }
    }

    private static @Nullable OwlType shoulderOwl(CompoundTag tag) {
        boolean isOwl = tag.getString("id")
                .flatMap(EntityType::byString)
                .filter(type -> type == OwlMod.OWL.get())
                .isPresent();
        return isOwl ? OwlType.fromTag(tag) : null;
    }
}
