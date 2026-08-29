package net.mehvahdjukaar.courier_owls.bird.pathfinding;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public final class Enclosure {
    private static final float EDGE_WEIGHT = (float) (1 / Math.sqrt(2));
    private static final float CORNER_WEIGHT = (float) (1 / Math.sqrt(3));
    private static final float FULLY_ENCLOSED_WEIGHT = 6 + 12 * EDGE_WEIGHT + 8 * CORNER_WEIGHT;

    private static final float[] CELL_WEIGHT = new float[27];
    private static final int[] LAYER_MASK = new int[3];

    static {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int axes = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (axes == 0) {
                        continue;
                    }
                    CELL_WEIGHT[shellIndex(dx, dy, dz)] = axes == 1 ? 1.0F : axes == 2 ? EDGE_WEIGHT : CORNER_WEIGHT;
                    LAYER_MASK[dy + 1] |= shellBit(dx, dy, dz);
                }
            }
        }
    }

    public static final Enclosure OPEN = new Enclosure(0);

    public static final StreamCodec<ByteBuf, Enclosure> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(Enclosure::of, e -> e.shell);

    private final int shell;
    private final float fraction;

    private Enclosure(int shell) {
        this.shell = shell;
        this.fraction = fractionOf(shell);
    }

    public static Enclosure of(int shell) {
        return shell == 0 ? OPEN : new Enclosure(shell);
    }

    public float fraction() {
        return this.fraction;
    }

    public double blockedFraction(int dy) {
        int mask = LAYER_MASK[dy + 1];
        return (double) Integer.bitCount(this.shell & mask) / Integer.bitCount(mask);
    }

    public boolean blockedAt(int dx, int dy, int dz) {
        return (this.shell & shellBit(dx, dy, dz)) != 0;
    }

    boolean allowsMove(int dx, int dy, int dz) {
        if (this.blockedAt(dx, dy, dz)) return false;
        if (dx != 0 && this.blockedAt(dx, 0, 0)) return false;
        if (dy != 0 && this.blockedAt(0, dy, 0)) return false;
        if (dz != 0 && this.blockedAt(0, 0, dz)) return false;
        boolean cornerMove = dx != 0 && dy != 0 && dz != 0;
        return !cornerMove
                || !this.blockedAt(dx, dy, 0) && !this.blockedAt(dx, 0, dz) && !this.blockedAt(0, dy, dz);
    }

    private static float fractionOf(int shell) {
        float blocked = 0;
        for (int bits = shell; bits != 0; bits &= bits - 1) {
            blocked += CELL_WEIGHT[Integer.numberOfTrailingZeros(bits)];
        }
        return blocked / FULLY_ENCLOSED_WEIGHT;
    }

    static int shellBit(int dx, int dy, int dz) {
        return 1 << shellIndex(dx, dy, dz);
    }

    private static int shellIndex(int dx, int dy, int dz) {
        return (dx + 1) * 9 + (dy + 1) * 3 + (dz + 1);
    }
}
