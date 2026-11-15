package dev.stillworld.workshop;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class AuxiliaryBlockEntity extends BlockEntity {
    private int biomeIndex = -1;
    @Nullable private BlockPos owner;
    public AuxiliaryBlockEntity(BlockPos pos, BlockState state) { super(WorkshopContent.AUXILIARY_ENTITY.get(), pos, state); }
    public int biomeIndex() { return biomeIndex; }
    @Nullable public BlockPos owner() { return owner; }

    boolean availableFor(BlockPos center) {
        if (owner == null || owner.equals(center)) return true;
        if (level == null || !level.hasChunkAt(owner)) return false;
        return !(level.getBlockEntity(owner) instanceof StabilizerBlockEntity machine) || !machine.ownsActive(worldPosition);
    }
    void display(BlockPos center, int biome) {
        if (center.equals(owner) && biome == biomeIndex) return;
        owner = center.immutable(); biomeIndex = biome; sync();
    }
    void clear(BlockPos center) {
        if (!center.equals(owner)) return;
        owner = null; biomeIndex = -1; sync();
    }
    void invalidateOwner() {
        if (level != null && owner != null && level.hasChunkAt(owner)
                && level.getBlockEntity(owner) instanceof StabilizerBlockEntity machine) machine.structureBroken();
    }
    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    public static void tick(Level level, BlockPos pos, BlockState state, AuxiliaryBlockEntity auxiliary) {
        if (level.isClientSide || level.getGameTime() % 20 != 0 || auxiliary.owner == null) return;
        if (!level.hasChunkAt(auxiliary.owner)) {
            // Keep the ownership reservation while the center is unloaded, but hide its unverified display.
            if (auxiliary.biomeIndex != -1) { auxiliary.biomeIndex = -1; auxiliary.sync(); }
        } else if (!(level.getBlockEntity(auxiliary.owner) instanceof StabilizerBlockEntity machine)
                || !machine.ownsActive(pos)) auxiliary.clear(auxiliary.owner);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("BiomeIndex", biomeIndex);
        if (owner != null) tag.putLong("Owner", owner.asLong());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.contains("Owner") ? BlockPos.of(tag.getLong("Owner")) : null;
        int index = tag.getInt("BiomeIndex");
        biomeIndex = owner != null && index >= 0 && index < 8 ? index : -1;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
