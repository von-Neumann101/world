package dev.stillworld.snapshot;

import dev.stillworld.mixin.ChunkPacketAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class ChunkSnapshot {
    private final LevelChunk frozen;
    private final ClientboundLevelChunkWithLightPacket baseline;
    private ClientboundLevelChunkWithLightPacket packet;
    public ChunkSnapshot(ServerLevel level, LevelChunk source) {
        frozen = new LevelChunk(level, source.getPos());
        var original = source.getSections();
        for (int i = 0; i < original.length; i++) {
            frozen.getSections()[i] = new LevelChunkSection(original[i].getStates().copy(), original[i].getBiomes());
        }
        source.getHeightmaps().forEach(entry -> frozen.setHeightmap(entry.getKey(), entry.getValue().getRawData().clone()));
        source.getBlockEntities().forEach((pos, entity) -> {
            var clone = BlockEntity.loadStatic(pos, frozen.getBlockState(pos), entity.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
            if (clone != null) { clone.setLevel(level); frozen.getBlockEntities().put(pos.immutable(), clone); }
        });
        baseline = new ClientboundLevelChunkWithLightPacket(frozen, level.getLightEngine(), null, null);
        packet = baseline;
    }
    public LevelChunk chunk() { return frozen; }
    public BlockState state(BlockPos pos) { return frozen.getBlockState(pos); }
    public void set(BlockPos pos, BlockState state) {
        if (frozen.isOutsideBuildHeight(pos)) return;
        frozen.getSection(frozen.getSectionIndex(pos.getY())).setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state);
        frozen.getBlockEntities().remove(pos);
        if (state.getBlock() instanceof EntityBlock block) {
            var entity = block.newBlockEntity(pos, state);
            if (entity != null) { entity.setLevel(frozen.getLevel()); frozen.getBlockEntities().put(pos.immutable(), entity); }
        }
        packet = null;
    }
    public ClientboundLevelChunkWithLightPacket packet() {
        if (packet == null) {
            packet = new ClientboundLevelChunkWithLightPacket(frozen, frozen.getLevel().getLightEngine(), null, null);
            ((ChunkPacketAccessor)(Object)packet).stillworld$light(baseline.getLightData());
        }
        return packet;
    }
}
