package net.mehvahdjukaar.courier_owls.bird.pathfinding;

import net.minecraft.core.Vec3i;

record CellKeyPacker(int originX, int originY, int originZ) {
    CellKeyPacker(Vec3i origin) {
        this(origin.getX(), origin.getY(), origin.getZ());
    }

    long pack(int x, int y, int z) {
        long key = 0;
        key |= ((long) (x - this.originX) & 0x1FFF);
        key |= ((long) (y - this.originY) & 0x3FF) << 13;
        key |= ((long) (z - this.originZ) & 0x1FFF) << 23;
        return key;
    }

    int unpackX(long key) {
        return this.originX + (int) (key << 51 >> 51);
    }

    int unpackY(long key) {
        return this.originY + (int) (key << 41 >> 54);
    }

    int unpackZ(long key) {
        return this.originZ + (int) (key << 28 >> 51);
    }
}
