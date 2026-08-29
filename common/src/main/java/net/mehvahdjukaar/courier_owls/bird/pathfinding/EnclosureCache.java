package net.mehvahdjukaar.courier_owls.bird.pathfinding;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.Vec3i;

final class EnclosureCache {
    interface ClearanceTest {
        boolean isClear(int x, int y, int z);
    }

    private final ClearanceTest clearance;
    private final Long2ObjectMap<Enclosure> cache = new Long2ObjectOpenHashMap<>();
    private CellKeyPacker keys;

    EnclosureCache(ClearanceTest clearance) {
        this.clearance = clearance;
    }

    void prepare(Vec3i origin) {
        this.cache.clear();
        this.keys = new CellKeyPacker(origin);
    }

    void done() {
        this.cache.clear();
    }

    Enclosure enclosureAt(int x, int y, int z) {
        long key = this.cellKey(x, y, z);
        Enclosure cached = this.cache.get(key);
        if (cached != null) {
            return cached;
        }
        Enclosure measured = Enclosure.of(this.measureShell(x, y, z));
        this.cache.put(key, measured);
        return measured;
    }

    private int measureShell(int x, int y, int z) {
        int shell = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean isCenter = dx == 0 && dy == 0 && dz == 0;
                    if (!isCenter && !this.clearance.isClear(x + dx, y + dy, z + dz)) {
                        shell |= Enclosure.shellBit(dx, dy, dz);
                    }
                }
            }
        }
        return shell;
    }

    long cellKey(int x, int y, int z) {
        return this.keys.pack(x, y, z);
    }
}
