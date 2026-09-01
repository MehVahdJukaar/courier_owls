package net.mehvahdjukaar.courier_owls.owls.blocks;

import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlAi;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlEntity;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlType;
import net.mehvahdjukaar.courier_owls.owls.nest.NestClaim;
import net.mehvahdjukaar.moonlight.api.block.ItemDisplayTile;
import net.minecraft.core.BlockPos;
import net.mehvahdjukaar.courier_owls.BirdMod;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class BirdNestBlockEntity extends ItemDisplayTile {
    public static final int SLOTS = 4;

    private static final String OCCUPANT_TAG = "Occupant";
    private static final String CLAIMANT_TAG = "Claimant";
    private static final String CLAIMANT_OWNER_TAG = "ClaimantOwner";
    private static final String SHELTERING_TAG = "Sheltering";

    public static final int VISIT_TICKS = 100;

    private static final List<String> IGNORED_OWL_TAGS = List.of(
            "Pos", "Motion", "Rotation", "FallDistance", "OnGround", "PortalCooldown", "Passengers", "leash");

    @Nullable
    private CompoundTag occupant;

    private boolean sheltering;

    @Nullable
    private UUID claimant;

    @Nullable
    private String claimantOwnerName;

    public BirdNestBlockEntity(BlockPos pos, BlockState state) {
        super(OwlMod.BIRD_NEST_TILE.get(), pos, state, SLOTS);
    }

    public int slotToInteract(ItemStack held) {
        if (held.isEmpty()) {
            for (int i = SLOTS - 1; i >= 0; i--) {
                if (!this.getItem(i).isEmpty()) return i;
            }
            return 0;
        }
        for (int i = 0; i < SLOTS; i++) {
            if (this.getItem(i).isEmpty()) return i;
        }
        return SLOTS - 1;
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return this.getItem(index).isEmpty();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory) {
        return new BirdNestMenu(id, playerInventory, this);
    }

    @Override
    public void onItemRemoved(Player player, ItemStack stack, int slot) {
        super.onItemRemoved(player, stack, slot);
        if (level != null && !level.isClientSide()) {
            level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS,
                    1.0F, level.getRandom().nextFloat() * 0.1F + 0.95F);
        }
    }

    @Nullable
    public UUID getClaimant() {
        return claimant;
    }

    @Nullable
    public String getClaimantOwnerName() {
        return claimantOwnerName;
    }

    public void setClaimant(@Nullable UUID claimant, @Nullable String ownerName) {
        this.claimant = claimant;
        this.claimantOwnerName = claimant == null ? null : ownerName;
        this.setChanged();
    }

    public boolean isOccupied() {
        return occupant != null;
    }

    public boolean isSheltering() {
        return sheltering;
    }

    public void settleFreshOwl(OwlType type, UUID owlId) {
        try (ProblemReporter.ScopedCollector reporter =
                     new ProblemReporter.ScopedCollector(this.problemPath(), BirdMod.LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithoutContext(reporter);
            output.putString("id", EntityType.getKey(OwlMod.OWL.get()).toString());
            output.store("UUID", UUIDUtil.CODEC, owlId);
            OwlType.save(output, type);
            occupant = output.buildResult();
        }
        sheltering = true;
        this.setClaimant(owlId, null);
        this.setChanged();
    }

    public boolean tryEnter(OwlEntity owl) {
        if (occupant != null || level == null || level.isClientSide()) return false;
        owl.stopRiding();
        owl.ejectPassengers();
        this.shelveCarriedItem(owl);
        try (ProblemReporter.ScopedCollector reporter =
                     new ProblemReporter.ScopedCollector(owl.problemPath(), BirdMod.LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, owl.registryAccess());
            if (!owl.save(output)) return false;
            IGNORED_OWL_TAGS.forEach(output::discard);
            occupant = output.buildResult();
        }

        sheltering = !owl.isTame() && (OwlAi.isSleepTime(level) || OwlAi.isRainingAround(level, worldPosition));

        level.gameEvent(GameEvent.BLOCK_CHANGE, worldPosition, GameEvent.Context.of(owl, this.getBlockState()));
        level.playSound(null, worldPosition, OwlMod.OWL_BURROW_ENTER.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        owl.discard();
        setOccupiedState(true);

        level.scheduleTick(worldPosition, this.getBlockState().getBlock(), VISIT_TICKS);
        this.setChanged();
        return true;
    }

    private void shelveCarriedItem(OwlEntity owl) {
        ItemStack carried = owl.getTalonItem();
        if (carried.isEmpty()) return;
        for (int i = 0; i < SLOTS; i++) {
            if (this.getItem(i).isEmpty()) {
                this.setItem(i, carried);
                owl.setTalonItem(ItemStack.EMPTY);
                return;
            }
        }
    }

    @Nullable
    public OwlEntity releaseOccupant() {
        if (occupant == null || level == null || level.isClientSide()) return null;
        Direction facing = this.getBlockState().getValue(BirdNestBlock.FACING);
        BlockPos front = worldPosition.relative(facing);
        if (!level.getBlockState(front).getCollisionShape(level, front).isEmpty()) return null;

        Entity spawned = spawnOccupant(facing);
        setOccupiedState(false);
        this.setChanged();
        return spawned instanceof OwlEntity owl ? owl : null;
    }

    public void dropOccupant() {
        spawnOccupant(null);
    }

    public void forgetOccupant() {
        occupant = null;
        sheltering = false;
        this.setChanged();
    }

    @Nullable
    private Entity spawnOccupant(@Nullable Direction facing) {
        if (occupant == null || level == null || level.isClientSide()) return null;
        CompoundTag tag = occupant;
        occupant = null;
        sheltering = false;
        Entity spawned = EntityType.loadEntityRecursive(tag, level, EntitySpawnReason.LOAD, e -> e);
        if (spawned == null) return null;

        Vec3i step = facing == null ? Vec3i.ZERO : facing.getUnitVec3i();
        double sideways = facing == null ? 0 : 0.55 + spawned.getBbWidth() / 2f;
        float yRot = facing == null ? spawned.getYRot() : facing.toYRot();
        spawned.snapTo(
                worldPosition.getX() + 0.5 + sideways * step.getX(),
                worldPosition.getY() + 0.5 - spawned.getBbHeight() / 2f,
                worldPosition.getZ() + 0.5 + sideways * step.getZ(),
                yRot, 0);

        if (spawned instanceof LivingEntity living) {
            living.setYBodyRot(yRot);
            living.setYHeadRot(yRot);
        }

        if (!level.addFreshEntity(spawned)) return null;

        if (spawned instanceof OwlEntity owl && spawned.getUUID().equals(claimant)
                && NestClaim.of(owl) == null) {
            NestClaim.claim(owl, worldPosition);
        }
        level.playSound(null, worldPosition, OwlMod.OWL_BURROW_EXIT.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(GameEvent.BLOCK_CHANGE, worldPosition, GameEvent.Context.of(spawned, this.getBlockState()));
        return spawned;
    }

    private void setOccupiedState(boolean occupied) {
        BlockState state = this.getBlockState();
        if (level == null || state.getValue(BirdNestBlock.OCCUPIED) == occupied) return;
        level.setBlock(worldPosition, state.setValue(BirdNestBlock.OCCUPIED, occupied), Block.UPDATE_ALL);
    }

    public int getRedstoneSignal() {
        int filled = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (!this.getItem(i).isEmpty()) filled++;
        }
        if (isOccupied()) filled++;
        return filled == 0 ? 0 : 1 + (filled * 14) / (SLOTS + 1);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);

        tag.remove(OCCUPANT_TAG);
        return tag;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        occupant = input.read(OCCUPANT_TAG, CompoundTag.CODEC).orElse(null);
        claimant = input.read(CLAIMANT_TAG, UUIDUtil.CODEC).orElse(null);
        claimantOwnerName = input.getString(CLAIMANT_OWNER_TAG).orElse(null);
        sheltering = input.getBooleanOr(SHELTERING_TAG, false);
    }

    @Override
    public void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable(OCCUPANT_TAG, CompoundTag.CODEC, occupant);
        output.storeNullable(CLAIMANT_TAG, UUIDUtil.CODEC, claimant);
        output.storeNullable(CLAIMANT_OWNER_TAG, Codec.STRING, claimantOwnerName);
        if (sheltering) output.putBoolean(SHELTERING_TAG, true);
    }
}
