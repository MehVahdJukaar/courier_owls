package net.mehvahdjukaar.courier_owls.owls.delivery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.mehvahdjukaar.courier_owls.BirdMod;
import net.mehvahdjukaar.courier_owls.bird.util.FlightMath;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlEntity;
import net.mehvahdjukaar.moonlight.api.misc.WorldSavedData;
import net.mehvahdjukaar.moonlight.api.misc.WorldSavedDataType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class DeliveringOwlsStorage extends WorldSavedData {
    private static final int MIN_TICKS = 6 * 20;
    private static final int TICKS_PER_DOUBLING = 40;
    private static final double SHORTEST_HAUL = 64.0;

    private static final int ARRIVAL_RETRY_TICKS = 20 * 5;

    private static final int PRELOAD_TICKS = 60;

    private static final int TICKET_RADIUS = 2;

    private static final int TICKET_TICKS = 20 * 15;

    private static final TicketType PUFF_IN = new TicketType(TICKET_TICKS,
            TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION);

    public static final Codec<DeliveringOwlsStorage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Flight.CODEC.listOf().fieldOf("flights").forGetter(data -> data.flights)
    ).apply(instance, DeliveringOwlsStorage::new));

    private record Flight(CompoundTag owl, long arrivesAt, Optional<UUID> follows, Optional<BlockPos> place,
                         boolean waitsForThem) {
        static final Codec<Flight> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                CompoundTag.CODEC.fieldOf("owl").forGetter(Flight::owl),
                Codec.LONG.fieldOf("arrives_at").forGetter(Flight::arrivesAt),
                UUIDUtil.CODEC.optionalFieldOf("follows").forGetter(Flight::follows),
                BlockPos.CODEC.optionalFieldOf("place").forGetter(Flight::place),

                Codec.BOOL.optionalFieldOf("waits_for_them", false).forGetter(Flight::waitsForThem)
        ).apply(instance, Flight::new));

        Flight arrivingAt(long time) {
            return new Flight(this.owl, time, this.follows, this.place, this.waitsForThem);
        }
    }

    private final List<Flight> flights;

    private DeliveringOwlsStorage(List<Flight> flights) {
        this.flights = new ArrayList<>(flights);
    }

    public DeliveringOwlsStorage(ServerLevel level) {
        this(List.of());
    }

    public static DeliveringOwlsStorage of(ServerLevel level) {
        return Objects.requireNonNull(OwlMod.PUFFED_OWLS.getData(level));
    }

    @Override
    public WorldSavedDataType<DeliveringOwlsStorage> getType() {
        return OwlMod.PUFFED_OWLS;
    }

    public static long arrivalTime(ServerLevel level, OwlEntity owl, Vec3 destination) {
        return level.getGameTime() + travelTicks(owl.position().distanceTo(destination));
    }

    private static long travelTicks(double blocks) {
        double doublings = Math.log(Math.max(blocks, SHORTEST_HAUL) / SHORTEST_HAUL) / Math.log(2);
        return (long) (MIN_TICKS + doublings * TICKS_PER_DOUBLING);
    }

    public void puffOutTo(OwlEntity owl, Player follows, long arrivesAt) {
        this.puffOut(owl, arrivesAt, Optional.of(follows.getUUID()), Optional.empty(), false);
    }

    public void puffOutTo(OwlEntity owl, BlockPos place, long arrivesAt) {
        this.puffOut(owl, arrivesAt, Optional.empty(), Optional.of(place), false);
    }

    public void puffOutWaitingFor(OwlEntity owl, UUID follows, long arrivesAt) {
        this.puffOut(owl, arrivesAt, Optional.of(follows), Optional.empty(), true);
    }

    private void puffOut(OwlEntity owl, long arrivesAt, Optional<UUID> follows, Optional<BlockPos> place,
                         boolean waitsForThem) {
        CompoundTag tag;
        try (ProblemReporter.ScopedCollector reporter =
                     new ProblemReporter.ScopedCollector(owl.problemPath(), BirdMod.LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, owl.registryAccess());
            if (!owl.save(output)) {
                return;
            }
            tag = output.buildResult();
        }
        owl.puff();
        this.flights.add(new Flight(tag, arrivesAt, follows, place, waitsForThem));
        owl.discard();
        this.setDirty();
    }

    public void tick(ServerLevel level) {
        if (this.flights.isEmpty()) {
            return;
        }
        long gameTime = level.getGameTime();
        boolean changed = false;
        ListIterator<Flight> flights = this.flights.listIterator();
        while (flights.hasNext()) {
            Flight flight = flights.next();
            if (gameTime < flight.arrivesAt()) {
                if (gameTime >= flight.arrivesAt() - PRELOAD_TICKS) {
                    holdArrivalChunk(level, flight);
                }
                continue;
            }
            if (flight.waitsForThem() && destinationOf(level, flight) == null) {
                continue;
            }
            changed = true;
            if (puffIn(level, flight)) {
                flights.remove();
            } else {
                flights.set(flight.arrivingAt(gameTime + ARRIVAL_RETRY_TICKS));
            }
        }
        if (changed) {
            this.setDirty();
        }
    }

    private static void holdArrivalChunk(ServerLevel level, Flight flight) {
        Vec3 destination = destinationOf(level, flight);
        if (destination == null) {
            return;
        }
        ChunkPos chunk = ChunkPos.containing(BlockPos.containing(destination));
        level.getChunkSource().addTicketWithRadius(PUFF_IN, chunk, TICKET_RADIUS);
    }

    private static boolean puffIn(ServerLevel level, Flight flight) {
        Entity entity = EntityType.loadEntityRecursive(flight.owl(), level, EntitySpawnReason.LOAD, EntityProcessor.NOP);
        if (!(entity instanceof OwlEntity owl)) {
            BirdMod.LOGGER.warn("a puffed owl could not be rebuilt and is gone, with whatever it was carrying");
            return true;
        }
        Vec3 destination = destinationOf(level, flight);

        if (destination != null && !arriveNear(level, owl, destination)) {
            return false;
        }
        if (!level.addFreshEntity(owl)) {
            return false;
        }
        owl.puff();
        return true;
    }

    @Nullable
    private static Vec3 destinationOf(ServerLevel level, Flight flight) {
        Player follows = flight.follows().map(level::getPlayerByUUID).orElse(null);
        if (follows != null) {
            return follows.position();
        }
        return flight.place().map(Vec3::atCenterOf).orElse(null);
    }

    private static boolean arriveNear(ServerLevel level, OwlEntity owl, Vec3 destination) {
        BlockPos spot = PuffSpot.toArriveAt(level, owl, destination);
        if (spot == null) {
            return false;
        }
        Vec3 at = Vec3.atBottomCenterOf(spot);
        float facing = FlightMath.yawTowards(destination.x - at.x, destination.z - at.z);
        owl.snapTo(at.x, at.y, at.z, facing, 0.0F);
        owl.setYBodyRot(facing);
        owl.setYHeadRot(facing);
        return true;
    }
}
