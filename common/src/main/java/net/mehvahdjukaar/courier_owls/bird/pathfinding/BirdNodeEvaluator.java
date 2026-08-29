package net.mehvahdjukaar.courier_owls.bird.pathfinding;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.mehvahdjukaar.courier_owls.bird.entity.BaseBirdMob;
import net.mehvahdjukaar.courier_owls.bird.entity.BirdSettings;
import net.mehvahdjukaar.courier_owls.bird.util.FlightMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.FlyNodeEvaluator;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class BirdNodeEvaluator extends FlyNodeEvaluator {
    public static final int HEADING_BINS = 8;

    public static final int CLIMB_STATES = 3;

    private static final int[] BIN_DX = {1, 1, 0, -1, -1, -1, 0, 1};
    private static final int[] BIN_DZ = {0, 1, 1, 1, 0, -1, -1, -1};

    private record Move(int dx, int dy, int dz, int headingBin) {
    }

    private static final Move[] MOVES = new Move[26];

    static {
        int i = 0;
        for (int bin = 0; bin < HEADING_BINS; bin++) {
            for (int dy = -1; dy <= 1; dy++) {
                MOVES[i++] = new Move(BIN_DX[bin], dy, BIN_DZ[bin], bin);
            }
        }
        MOVES[i++] = new Move(0, 1, 0, -1);
        MOVES[i] = new Move(0, -1, 0, -1);
    }

    private final Long2ObjectMap<BirdNode> latticeNodes = new Long2ObjectOpenHashMap<>();

    private final EnclosureCache enclosureCache = new EnclosureCache(this::isClear);
    private boolean startsGrounded;

    private PathfindingSettings settings = BirdSettings.DEFAULTS.search();

    public static int headingBinOf(double dx, double dz) {
        return Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dz, dx)) / 45.0), HEADING_BINS);
    }

    @Override
    public void prepare(PathNavigationRegion level, Mob mob) {
        super.prepare(level, mob);
        this.latticeNodes.clear();
        this.enclosureCache.prepare(mob.blockPosition());
        if (mob instanceof BaseBirdMob bird) {
            this.startsGrounded = bird.isOnFoot();
            this.settings = bird.settings().search();
        } else {
            this.startsGrounded = false;
            this.settings = BirdSettings.DEFAULTS.search();
        }
    }

    @Override
    public void done() {
        this.latticeNodes.clear();
        this.enclosureCache.done();
        super.done();
    }

    @Override
    public Node getStart() {
        Node vanillaStart = super.getStart();
        BirdNode start = this.getLatticeNode(vanillaStart.x, vanillaStart.y, vanillaStart.z,
                yawToBin(this.mob.getYRot()), climbOf(this.mob.getDeltaMovement()), this.startsGrounded);
        start.startInFlight = !this.startsGrounded;
        start.type = vanillaStart.type;
        start.costMalus = vanillaStart.costMalus;
        return start;
    }

    @Override
    public int getNeighbors(Node[] outputArray, Node node) {
        int count = 0;

        int heading = node instanceof BirdNode bird ? bird.heading : yawToBin(this.mob.getYRot());
        boolean free = node instanceof BirdNode bird && bird.freeHeading;

        Enclosure around = node instanceof BirdNode bird ? bird.enclosure : null;

        for (Move move : MOVES) {
            BirdNode neighbor = this.successor(node, move, heading, free, around);
            if (neighbor != null) {
                outputArray[count++] = neighbor;
            }
        }
        return count;
    }

    @Nullable
    private BirdNode successor(Node from, Move move, int heading, boolean free, @Nullable Enclosure around) {
        int moveBin = move.headingBin;

        int climb = move.dy;

        boolean stillFree = free && moveBin < 0;

        boolean clear = around != null
                ? around.allowsMove(move.dx, climb, move.dz)
                : this.hasClearance(from.x, from.y, from.z, move.dx, climb, move.dz);
        if (!clear) {
            return null;
        }
        BirdNode neighbor = this.findAcceptedLatticeNode(from.x + move.dx, from.y + climb, from.z + move.dz,
                moveBin < 0 ? heading : moveBin, climb, stillFree);
        return neighbor == null || neighbor.closed ? null : neighbor;
    }

    @Nullable
    private BirdNode findAcceptedLatticeNode(int x, int y, int z, int heading, int climb, boolean freeHeading) {
        PathType type = this.getCachedPathType(x, y, z);
        float malus = this.cellMalus(type);
        if (malus < 0.0F) {
            return null;
        }
        BirdNode node = this.getLatticeNode(x, y, z, heading, climb, freeHeading);
        node.type = type;
        node.costMalus = malus;
        return node;
    }

    private float cellMalus(PathType type) {
        float malus = this.mob.getPathfindingMalus(type);
        return malus >= 0.0F && type == PathType.WALKABLE ? malus + 1.0F : malus;
    }

    private BirdNode getLatticeNode(int x, int y, int z, int heading, int climb, boolean freeHeading) {
        return this.latticeNodes.computeIfAbsent(this.stateKey(x, y, z, heading, climb, freeHeading),
                key -> new BirdNode(x, y, z, heading, climb, freeHeading, this.enclosureCache.enclosureAt(x, y, z)));
    }

    private boolean hasClearance(int x, int y, int z, int dx, int dy, int dz) {
        int axes = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
        if (axes < 2) {
            return true;
        }
        if (dx != 0 && !this.isClear(x + dx, y, z)) return false;
        if (dy != 0 && !this.isClear(x, y + dy, z)) return false;
        if (dz != 0 && !this.isClear(x, y, z + dz)) return false;
        if (axes == 3) {
            return this.isClear(x + dx, y + dy, z)
                    && this.isClear(x + dx, y, z + dz)
                    && this.isClear(x, y + dy, z + dz);
        }
        return true;
    }

    RescueFlood.Stepper rescueFloodStepper() {
        return (x, y, z, out) -> {
            Enclosure around = this.enclosureCache.enclosureAt(x, y, z);
            for (Move move : MOVES) {
                if (!around.allowsMove(move.dx, move.dy, move.dz)) {
                    continue;
                }
                int nx = x + move.dx, ny = y + move.dy, nz = z + move.dz;
                float malus = this.cellMalus(this.getCachedPathType(nx, ny, nz));
                if (malus < 0.0F) {
                    continue;
                }
                int axes = Math.abs(move.dx) + Math.abs(move.dy) + Math.abs(move.dz);
                float step = Mth.sqrt(axes) + malus;
                if (move.dx == 0 && move.dz == 0) {
                    step += this.settings.straightVerticalCost;
                }
                if (this.settings.enclosureCost > 0) {
                    step += this.settings.enclosureCost
                            * this.enclosureCache.enclosureAt(nx, ny, nz).fraction();
                }
                out.accept(nx, ny, nz, step);
            }
        };
    }

    List<BlockPos> walkableAround(Collection<BlockPos> targets, int radius, int rise, int maxSteps) {
        Set<BlockPos> reached = new LinkedHashSet<>();
        List<BlockPos> frontier = new ArrayList<>();
        for (BlockPos target : targets) {
            if (this.isStandable(target) && reached.add(target)) {
                frontier.add(target);
            }
        }
        for (int stepsTaken = 0; stepsTaken < maxSteps && !frontier.isEmpty(); stepsTaken++) {
            List<BlockPos> nextLayer = new ArrayList<>();
            for (BlockPos from : frontier) {
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos next = from.relative(side).above(dy);
                        if (!reached.contains(next) && withinRing(next, targets, radius, rise)
                                && this.isStandable(next)) {
                            reached.add(next);
                            nextLayer.add(next);
                        }
                    }
                }
            }
            frontier = nextLayer;
        }
        reached.removeAll(targets);
        return List.copyOf(reached);
    }

    private static boolean withinRing(BlockPos pos, Collection<BlockPos> targets, int radius, int rise) {
        for (BlockPos target : targets) {
            int dx = pos.getX() - target.getX();
            int dz = pos.getZ() - target.getZ();
            if (dx * dx + dz * dz <= radius * radius && Math.abs(pos.getY() - target.getY()) <= rise) {
                return true;
            }
        }
        return false;
    }

    private boolean isStandable(BlockPos pos) {
        PathType type = this.getCachedPathType(pos.getX(), pos.getY(), pos.getZ());
        return type == PathType.WALKABLE && this.mob.getPathfindingMalus(type) >= 0.0F;
    }

    Iterable<BirdNode> knownStates() {
        return this.latticeNodes.values();
    }

    private boolean isClear(int x, int y, int z) {
        return this.mob.getPathfindingMalus(this.getCachedPathType(x, y, z)) >= 0.0F;
    }

    private long stateKey(int x, int y, int z, int heading, int climb, boolean freeHeading) {
        long key = this.enclosureCache.cellKey(x, y, z);
        key |= ((long) heading & 0x7) << 36;
        key |= (freeHeading ? 1L : 0L) << 39;
        key |= ((long) (climb + 1) & 0x3) << 40;
        return key;
    }

    static int turnAmount(int headingA, int headingB) {
        int diff = Math.abs(headingA - headingB) % HEADING_BINS;
        return Math.min(diff, HEADING_BINS - diff);
    }

    static int pitchAmount(int climbA, int climbB) {
        return Math.abs(climbA - climbB);
    }

    static int turnBinsToward(int heading, int dx, int dz) {
        if (dx == 0 && dz == 0) {
            return 0;
        }
        double bearingBins = Math.atan2(dz, dx) * HEADING_BINS / (2 * Math.PI);
        double diff = Math.abs(bearingBins - heading) % HEADING_BINS;
        return (int) Math.min(diff, HEADING_BINS - diff);
    }

    static int climbOf(Vec3 velocity) {
        double pitchDegrees = FlightMath.pitchOf(velocity) * Mth.RAD_TO_DEG;
        return Mth.clamp(Math.round((float) pitchDegrees / 45.0F), -1, 1);
    }

    static int yawToBin(float yRot) {
        return Math.floorMod(Math.round((yRot + 90.0F) / (360.0F / HEADING_BINS)), HEADING_BINS);
    }
}
