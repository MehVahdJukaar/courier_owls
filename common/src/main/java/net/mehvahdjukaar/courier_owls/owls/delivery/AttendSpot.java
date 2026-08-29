package net.mehvahdjukaar.courier_owls.owls.delivery;

import net.mehvahdjukaar.courier_owls.bird.entity.BaseBirdMob;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class AttendSpot {
    public static final double STANDOFF = 1.5;

    private static final double NEAREST = 1.0;
    private static final double FURTHEST = 2.3;
    private static final double HEIGHT_SLACK = 0.8;

    private static final int SIDES = 8;

    public static double heightFor(Player player, BaseBirdMob owl) {
        return player.getEyeY() - owl.getBbHeight() * 0.5;
    }

    public static boolean stillBeside(Vec3 spot, Player player, BaseBirdMob owl) {
        double dx = spot.x - player.getX();
        double dz = spot.z - player.getZ();
        double reach = Math.sqrt(dx * dx + dz * dz);
        return reach >= NEAREST && reach <= FURTHEST
                && Math.abs(spot.y - heightFor(player, owl)) <= HEIGHT_SLACK;
    }

    public static Vec3 pick(Player player, BaseBirdMob owl) {
        Vec3 bearing = sideOwlIsOn(player, owl);
        double height = heightFor(player, owl);
        for (int side = 0; side < SIDES; side++) {
            Vec3 turned = bearing.yRot((float) (side * Math.PI * 2.0 / SIDES));
            Vec3 spot = new Vec3(player.getX() + turned.x * STANDOFF, height, player.getZ() + turned.z * STANDOFF);
            if (owl.level().noCollision(owl, owl.getBoundingBox().move(spot.subtract(owl.position())))) {
                return spot;
            }
        }
        return new Vec3(player.getX() + bearing.x * STANDOFF, height, player.getZ() + bearing.z * STANDOFF);
    }

    private static Vec3 sideOwlIsOn(Player player, BaseBirdMob owl) {
        Vec3 gap = new Vec3(owl.getX() - player.getX(), 0.0, owl.getZ() - player.getZ());
        if (gap.lengthSqr() > 1.0E-4) {
            return gap.normalize();
        }
        float yaw = player.getYHeadRot() * Mth.DEG_TO_RAD;
        return new Vec3(Mth.sin(yaw), 0.0, -Mth.cos(yaw));
    }
}
