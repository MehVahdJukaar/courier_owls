package net.mehvahdjukaar.courier_owls.bird.navigator;

import net.mehvahdjukaar.courier_owls.bird.entity.BaseBirdMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class BirdWalkNavigation extends GroundPathNavigation {
    private static final double LAST_NODE_ACCURACY = 0.25;

    private final BaseBirdMob bird;

    public BirdWalkNavigation(BaseBirdMob mob, Level level) {
        super(mob, level);
        this.bird = mob;
    }

    @Override
    public boolean moveTo(double x, double y, double z, double speed) {
        return this.moveTo(x, y, z, 1, speed);
    }

    @Override
    public boolean moveTo(Entity entity, double speed) {
        return this.moveTo(entity.getX(), entity.getY(), entity.getZ(), 1, speed);
    }

    @Override
    @Nullable
    public Path createPath(BlockPos target, int accuracy) {
        return this.isInTheAir(target) ? null : super.createPath(target, accuracy);
    }

    public boolean isInTheAir(BlockPos target) {
        return this.level.getBlockState(target).isAir() && this.level.getBlockState(target.below()).isAir();
    }

    @Override
    protected void followThePath() {
        Path path = this.path;
        boolean onTheLastNode = path != null && path.getNextNodeIndex() == path.getNodeCount() - 1;
        if (!onTheLastNode) {
            super.followThePath();
            return;
        }
        Vec3 wasAt = this.getTempMobPos();
        Vec3i node = path.getNextNodePos();
        boolean standingOnIt = Math.abs(this.mob.getX() - (node.getX() + 0.5)) < LAST_NODE_ACCURACY
                && Math.abs(this.mob.getZ() - (node.getZ() + 0.5)) < LAST_NODE_ACCURACY
                && Math.abs(this.mob.getY() - node.getY()) < 1.0;
        if (standingOnIt) {
            path.advance();
        }
        this.doStuckDetection(wasAt);
    }

    @Override
    public boolean moveTo(double x, double y, double z, int accuracy, double speed) {
        return this.bird.trip().walkOrFlyTo(BlockPos.containing(x, y, z), accuracy, speed);
    }
}
