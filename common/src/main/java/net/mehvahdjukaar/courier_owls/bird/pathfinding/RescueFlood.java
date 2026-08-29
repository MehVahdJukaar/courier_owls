package net.mehvahdjukaar.courier_owls.bird.pathfinding;

import it.unimi.dsi.fastutil.longs.Long2FloatMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongHeapPriorityQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;


class RescueFlood {
    @FunctionalInterface
    interface Stepper {
        void neighbors(int x, int y, int z, NeighborSink out);
    }

    @FunctionalInterface
    interface NeighborSink {
        void accept(int x, int y, int z, float stepCost);
    }

    private static final int F_SHIFT = 36;
    private static final long KEY_MASK = (1L << F_SHIFT) - 1;
    private static final float F_SCALE = 1024.0F;

    private static final long MAX_QUANTIZED_F = (1L << 27) - 1;

    private final Stepper stepper;
    private final int settleBudget;

    private final Vec3i start;
    private final CellKeyPacker keyPacker;

    private final Long2FloatMap bestG = new Long2FloatOpenHashMap();
    private final LongSet settled = new LongOpenHashSet();
    private final LongHeapPriorityQueue queue = new LongHeapPriorityQueue();

    private float maxSettledF;

    RescueFlood(Stepper stepper, int settleBudget, Vec3i start) {
        this.stepper = stepper;
        this.settleBudget = settleBudget;
        this.start = start;
        this.keyPacker = new CellKeyPacker(start);
        this.bestG.defaultReturnValue(Float.POSITIVE_INFINITY);
    }

    void seed(Vec3i goal) {
        this.offer(goal.getX(), goal.getY(), goal.getZ(), 0);
    }

    float distanceTo(int x, int y, int z) {
        long asked = this.keyPacker.pack(x, y, z);
        if (this.settled.contains(asked)) {
            return this.bestG.get(asked);
        }
        return this.pumpUntilSettled(asked, x, y, z);
    }

    private float pumpUntilSettled(long asked, int x, int y, int z) {
        while (!this.settled.contains(asked)) {
            if (this.settled.size() >= this.settleBudget) {
                return Math.max(0.0F, this.maxSettledF - this.boundToStart(x, y, z));
            }
            if (this.queue.isEmpty()) {
                return Float.POSITIVE_INFINITY;
            }
            long cell = this.queue.dequeueLong() & KEY_MASK;
            if (!this.settled.add(cell)) {
                continue;
            }
            float g = this.bestG.get(cell);
            int cx = this.keyPacker.unpackX(cell), cy = this.keyPacker.unpackY(cell), cz = this.keyPacker.unpackZ(cell);
            this.maxSettledF = Math.max(this.maxSettledF, g + this.boundToStart(cx, cy, cz));
            this.stepper.neighbors(cx, cy, cz,
                    (nx, ny, nz, stepCost) -> this.offer(nx, ny, nz, g + stepCost));
        }
        return this.bestG.get(asked);
    }

    boolean isSettled(int x, int y, int z) {
        return this.settled.contains(this.keyPacker.pack(x, y, z));
    }

    int settledCount() {
        return this.settled.size();
    }

    private void offer(int x, int y, int z, float g) {
        long key = this.keyPacker.pack(x, y, z);
        if (this.settled.contains(key) || g >= this.bestG.get(key)) {
            return;
        }
        this.bestG.put(key, g);
        long quantizedF = Math.min((long) ((g + this.boundToStart(x, y, z)) * F_SCALE), MAX_QUANTIZED_F);
        this.queue.enqueue(quantizedF << F_SHIFT | key);
    }

    private float boundToStart(int x, int y, int z) {
        return (float) Mth.length(x - this.start.getX(), y - this.start.getY(), z - this.start.getZ());
    }
}
