package dev.stillworld.snapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;

public final class SnapshotWorld implements BlockGetter {
    private final ServerLevel level;
    private final Map<Long, ChunkSnapshot> chunks = new HashMap<>();
    public SnapshotWorld(ServerLevel level) { this.level = level; }
    public ServerLevel level() { return level; }
    public void capture(LevelChunk chunk) {
        chunks.computeIfAbsent(chunk.getPos().toLong(), ignored -> new ChunkSnapshot(level, chunk));
    }
    public Collection<ChunkSnapshot> chunks() { return chunks.values(); }
    public ChunkSnapshot chunk(int x, int z) { return chunks.get(ChunkPos.asLong(x, z)); }
    public void setBlockState(BlockPos pos, BlockState state) {
        var chunk = chunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk != null) chunk.set(pos, state);
    }
    public List<VoxelShape> collisions(Entity entity, AABB area) {
        var result = new ArrayList<VoxelShape>();
        var box = Shapes.create(area);
        var context = CollisionContext.of(entity);
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(area.minX) - 1, Mth.floor(area.minY) - 1, Mth.floor(area.minZ) - 1,
                Mth.floor(area.maxX) + 1, Mth.floor(area.maxY) + 1, Mth.floor(area.maxZ) + 1)) {
            var state = getBlockState(pos);
            if (state.isAir()) continue;
            var shape = state.getCollisionShape(this, pos, context).move(pos.getX(), pos.getY(), pos.getZ());
            if (!shape.isEmpty() && Shapes.joinIsNotEmpty(shape, box, BooleanOp.AND)) result.add(shape);
        }
        return result;
    }
    @Override public BlockState getBlockState(BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) return Blocks.AIR.defaultBlockState();
        var chunk = chunk(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? Blocks.BARRIER.defaultBlockState() : chunk.state(pos);
    }
    @Override public BlockEntity getBlockEntity(BlockPos pos) {
        var chunk = chunk(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.chunk().getBlockEntities().get(pos);
    }
    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public int getHeight() { return level.getHeight(); }
    @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }
}
