package dev.stillworld.client;

import dev.stillworld.mixin.client.ChestLidAccessor;
import dev.stillworld.mixin.client.ChestLidHolderAccessor;
import dev.stillworld.mixin.client.ShulkerPoseAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.HashMap;
import java.util.Map;

/** Preserve client-only BE model progress when server snapshot packets replace the real chunk's BE instances. */
public final class FrozenBlockEntities {
    private interface Pose { void apply(BlockEntity entity); }
    private record Saved(BlockEntityType<?> type, Pose pose) { }
    private static final Map<Long,Map<BlockPos,Saved>> CHUNKS = new HashMap<>();

    public static void beforeReplace(LevelChunk chunk) {
        if (!chunk.getLevel().isClientSide || !StillworldClient.active() || RealityWorld.isMirror(chunk.getLevel())) return;
        var saved = CHUNKS.computeIfAbsent(chunk.getPos().toLong(), ignored -> new HashMap<>());
        for (var entity : chunk.getBlockEntities().values()) {
            var pose = capture(entity);
            if (pose != null) saved.putIfAbsent(entity.getBlockPos().immutable(), new Saved(entity.getType(),pose));
        }
    }
    public static void afterReplace(LevelChunk chunk) {
        if (!chunk.getLevel().isClientSide || !StillworldClient.active() || RealityWorld.isMirror(chunk.getLevel())) return;
        var saved = CHUNKS.get(chunk.getPos().toLong());
        if (saved != null) for (var entry : saved.entrySet()) {
            var entity = chunk.getBlockEntity(entry.getKey(),LevelChunk.EntityCreationType.IMMEDIATE);
            if (entity != null && entity.getType() == entry.getValue().type) entry.getValue().pose.apply(entity);
        }
        // First-time distant chunks have no prior client animation state. Freeze their first snapshot baseline.
        beforeReplace(chunk);
    }
    public static void edited(ClientLevel level, BlockPos pos) {
        var saved = CHUNKS.get(ChunkPos.asLong(pos.getX() >> 4,pos.getZ() >> 4));
        if (saved != null) saved.remove(pos);
        var entity = level.getBlockEntity(pos);
        if (entity != null) {
            var pose = capture(entity);
            if (pose != null) CHUNKS.computeIfAbsent(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4),ignored->new HashMap<>())
                    .put(pos.immutable(),new Saved(entity.getType(),pose));
        }
    }
    public static void clear() { CHUNKS.clear(); }

    private static Pose capture(BlockEntity entity) {
        if (entity instanceof ChestBlockEntity || entity instanceof EnderChestBlockEntity) {
            var lid = (ChestLidAccessor)(Object)((ChestLidHolderAccessor)entity).stillworld$lid();
            return new Lid(lid.stillworld$open(),lid.stillworld$previous(),lid.stillworld$shouldOpen());
        }
        if (entity instanceof ShulkerBoxBlockEntity shulker) return new Shulker(shulker.getProgress(1),shulker.getProgress(0),shulker.getAnimationStatus());
        if (entity instanceof EnchantingTableBlockEntity book) return new Book(book.time,book.flip,book.oFlip,book.flipT,book.flipA,book.open,book.oOpen,book.rot,book.oRot,book.tRot);
        if (entity instanceof BellBlockEntity bell) return new Bell(bell.ticks,bell.shaking,bell.clickDirection);
        return null;
    }
    private record Lid(float current,float previous,boolean shouldOpen) implements Pose {
        @Override public void apply(BlockEntity entity) {
            var lid=(ChestLidAccessor)(Object)((ChestLidHolderAccessor)entity).stillworld$lid();
            lid.stillworld$open(current);lid.stillworld$previous(previous);lid.stillworld$shouldOpen(shouldOpen);
        }
    }
    private record Shulker(float current,float previous,ShulkerBoxBlockEntity.AnimationStatus status) implements Pose {
        @Override public void apply(BlockEntity entity) {
            var shulker=(ShulkerPoseAccessor)entity;shulker.stillworld$progress(current);shulker.stillworld$previous(previous);shulker.stillworld$status(status);
        }
    }
    private record Book(int time,float flip,float oldFlip,float targetFlip,float flipSpeed,float open,float oldOpen,float rotation,float oldRotation,float targetRotation) implements Pose {
        @Override public void apply(BlockEntity entity) {
            var book=(EnchantingTableBlockEntity)entity;book.time=time;book.flip=flip;book.oFlip=oldFlip;book.flipT=targetFlip;book.flipA=flipSpeed;
            book.open=open;book.oOpen=oldOpen;book.rot=rotation;book.oRot=oldRotation;book.tRot=targetRotation;
        }
    }
    private record Bell(int ticks,boolean shaking,Direction direction) implements Pose {
        @Override public void apply(BlockEntity entity) {
            var bell=(BellBlockEntity)entity;
            // Initialize vanilla's ringing invariant without sharing a live-entity list from the old BE.
            // This also permits a safe ordinary tick if exit precedes its restoring chunk by a frame.
            if ((shaking || ticks>0) && direction!=null) bell.triggerEvent(1,direction.get3DDataValue());
            bell.ticks=ticks;bell.shaking=shaking;bell.clickDirection=direction;
        }
    }
    private FrozenBlockEntities() { }
}
