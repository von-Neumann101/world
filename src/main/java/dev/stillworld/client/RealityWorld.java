package dev.stillworld.client;

import dev.stillworld.mixin.client.RealityBiomeAccessor;
import dev.stillworld.mixin.client.RealityLevelRendererAccessor;
import java.util.BitSet;
import java.util.Iterator;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;

/** Independent visual blocks and lighting, fed only by the session's live-world side channel. */
public final class RealityWorld {
    private static UUID session;
    private static MirrorLevel mirror;
    private static LevelRenderer renderer;
    // Bounded allocation, reused between sessions just like Minecraft's own render buffers.
    private static RenderBuffers buffers;
    private static int viewRadius;
    private static boolean allocatingSections;

    private RealityWorld() { }

    public static void begin(UUID newSession, ClientLevel source) {
        clear();
        var minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) return;
        if (buffers == null) buffers = new RenderBuffers(1);
        var newRenderer = new MirrorRenderer(minecraft, buffers);
        var originalData = source.getLevelData();
        var data = new ClientLevel.ClientLevelData(originalData.getDifficulty(), originalData.isHardcore(),
                originalData.getClearColorScale() == 1.0F);
        data.setDifficultyLocked(originalData.isDifficultyLocked());
        data.getGameRules().assignFrom(originalData.getGameRules(), null);
        viewRadius = minecraft.options.renderDistance().get();
        try {
            var newLevel = new MirrorLevel(source, data, newRenderer, viewRadius);
            session = newSession;
            mirror = newLevel;
            renderer = newRenderer;
            newLevel.setDefaultSpawnPos(originalData.getSpawnPos(), originalData.getSpawnAngle());
            newLevel.setGameTime(source.getGameTime());
            newLevel.setDayTime(source.getDayTime());
            // setDayTime normally re-enables daylight; preserve the source's initial rule.
            data.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(
                    originalData.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT), null);
            newLevel.setDayTimeFraction(source.getDayTimeFraction());
            newLevel.setDayTimePerTick(source.getDayTimePerTick());
            data.setRaining(originalData.isRaining());
            newLevel.setRainLevel(source.getRainLevel(1));
            newLevel.setThunderLevel(source.getThunderLevel(1));
            updateViewCenter(newLevel);
            // Post chains belong to the orb's offscreen target. They are initialized
            // once by drawReality, while that target is active, not against the main view here.
            newRenderer.setLevel(newLevel);
        } catch (RuntimeException | Error failure) {
            session = null;
            mirror = null;
            renderer = null;
            disposeRenderer(newRenderer);
            throw failure;
        } finally {
            // setLevel also changes the shared entity dispatcher, even outside a render.
            minecraft.getEntityRenderDispatcher().setLevel(minecraft.level);
        }
    }

    public static void receive(UUID packetSession, Packet<?> packet) {
        var target = mirror;
        if (target == null || !packetSession.equals(session)) return;
        if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
            acceptChunk(target, chunk);
        } else if (packet instanceof ClientboundBlockUpdatePacket block) {
            target.setServerVerifiedBlockState(block.getPos(), block.getBlockState(), 19);
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket blocks) {
            blocks.runUpdates((pos, state) -> target.setServerVerifiedBlockState(pos, state, 19));
        } else if (packet instanceof ClientboundLightUpdatePacket light) {
            target.queueLightUpdate(() -> applyLight(target, light.getX(), light.getZ(), light.getLightData()));
        } else if (packet instanceof ClientboundBlockEntityDataPacket blockEntity) {
            if (!blockEntity.getTag().isEmpty()) {
                target.getBlockEntity(blockEntity.getPos(), blockEntity.getType()).ifPresent(entity ->
                        entity.handleUpdateTag(blockEntity.getTag(), target.registryAccess()));
            }
        } else if (packet instanceof ClientboundBlockEventPacket event) {
            target.blockEvent(event.getPos(), event.getBlock(), event.getB0(), event.getB1());
        } else if (packet instanceof ClientboundSetTimePacket time) {
            target.setGameTime(time.getGameTime());
            target.setDayTime(time.getDayTime());
        } else if (packet instanceof ClientboundGameEventPacket weather) {
            applyWeather(target, weather);
        } else if (packet instanceof ClientboundForgetLevelChunkPacket forgotten) {
            forgetChunk(target, forgotten.pos());
        }
    }

    public static void tick() {
        var target = mirror;
        if (target == null) return;
        if (Minecraft.getInstance().level != target.source) {
            clear();
            return;
        }
        if (Minecraft.getInstance().isPaused()) return;
        updateViewCenter(target);
        target.pollLightUpdates();
        target.getLightEngine().runLightUpdates();
        target.tick(() -> true);
        target.tickVisualBlockEntities();
        target.updateSkyBrightness();
        renderer.tick();
    }

    public static void clear() {
        session = null;
        mirror = null;
        var oldRenderer = renderer;
        renderer = null;
        viewRadius = 0;
        if (oldRenderer == null) return;
        var minecraft = Minecraft.getInstance();
        try {
            disposeRenderer(oldRenderer);
        } finally {
            minecraft.getEntityRenderDispatcher().setLevel(minecraft.level);
        }
    }

    @Nullable
    public static ClientLevel level() { return mirror; }

    @Nullable
    public static ClientLevel source() { return mirror == null ? null : mirror.source; }

    @Nullable
    public static LevelRenderer renderer() { return renderer; }

    @Nullable
    public static RenderBuffers buffers() { return buffers; }

    public static boolean isMirror(Level level) { return level instanceof MirrorLevel; }

    /** Only the mirror's view-area construction defers empty GPU mesh handles. */
    public static boolean deferVertexBuffers() { return allocatingSections; }

    private static final class MirrorRenderer extends LevelRenderer {
        private MirrorRenderer(Minecraft minecraft, RenderBuffers buffers) {
            super(minecraft,minecraft.getEntityRenderDispatcher(),minecraft.getBlockEntityRenderDispatcher(),buffers);
        }
        @Override public void allChanged() {
            boolean previous=allocatingSections;
            allocatingSections=true;
            try {super.allChanged();} finally {allocatingSections=previous;}
        }
    }

    private static void disposeRenderer(LevelRenderer oldRenderer) {
        try {
            oldRenderer.setLevel(null);
        } finally {
            try {
                oldRenderer.close();
            } finally {
                var access=(RealityLevelRendererAccessor)oldRenderer;
                for(var buffer:new com.mojang.blaze3d.vertex.VertexBuffer[]{access.stillworld$starBuffer(),
                        access.stillworld$skyBuffer(),access.stillworld$darkBuffer(),access.stillworld$cloudBuffer()})
                    if(buffer!=null)buffer.close();
            }
        }
    }

    private static void updateViewCenter(MirrorLevel target) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            var center = player.chunkPosition();
            target.getChunkSource().updateViewCenter(center.x, center.z);
        }
    }

    private static void acceptChunk(MirrorLevel target, ClientboundLevelChunkWithLightPacket packet) {
        updateViewCenter(target);
        var player = Minecraft.getInstance().player;
        if (player != null) {
            var center = player.chunkPosition();
            int needed = Math.max(Math.abs(packet.getX() - center.x), Math.abs(packet.getZ() - center.z));
            // These are already server-tracked chunks. Growing this local cache loads no world chunks.
            if (needed > viewRadius) {
                viewRadius = needed;
                target.getChunkSource().updateViewRadius(viewRadius);
            }
        }
        var data = packet.getChunkData();
        target.getChunkSource().replaceWithPacketData(packet.getX(), packet.getZ(), data.getReadBuffer(),
                data.getHeightmaps(), data.getBlockEntitiesTagsConsumer(packet.getX(), packet.getZ()));
        target.queueLightUpdate(() -> {
            applyLight(target, packet.getX(), packet.getZ(), packet.getLightData());
            var chunk = target.getChunkSource().getChunk(packet.getX(), packet.getZ(), false);
            if (chunk != null) enableChunkLight(target, chunk);
        });
    }

    private static void enableChunkLight(MirrorLevel target, LevelChunk chunk) {
        var light = target.getLightEngine();
        var sections = chunk.getSections();
        var pos = chunk.getPos();
        for (int index = 0; index < sections.length; index++) {
            int y = target.getSectionYFromSectionIndex(index);
            light.updateSectionStatus(SectionPos.of(pos, y), sections[index].hasOnlyAir());
            target.setSectionDirtyWithNeighbors(pos.x, y, pos.z);
        }
    }

    private static void applyLight(MirrorLevel target, int x, int z, ClientboundLightUpdatePacketData data) {
        var light = target.getLightEngine();
        readLightSections(target, x, z, light, LightLayer.SKY,
                data.getSkyYMask(), data.getEmptySkyYMask(), data.getSkyUpdates().iterator());
        readLightSections(target, x, z, light, LightLayer.BLOCK,
                data.getBlockYMask(), data.getEmptyBlockYMask(), data.getBlockUpdates().iterator());
        light.setLightEnabled(new ChunkPos(x, z), true);
    }

    private static void readLightSections(MirrorLevel target, int x, int z, LevelLightEngine light,
            LightLayer layer, BitSet present, BitSet empty, Iterator<byte[]> updates) {
        for (int index = 0; index < light.getLightSectionCount(); index++) {
            int y = light.getMinLightSection() + index;
            if (present.get(index) || empty.get(index)) {
                light.queueSectionData(layer, SectionPos.of(x, y, z),
                        present.get(index) ? new DataLayer(updates.next().clone()) : new DataLayer());
                target.setSectionDirtyWithNeighbors(x, y, z);
            }
        }
    }

    private static void forgetChunk(MirrorLevel target, ChunkPos pos) {
        target.getChunkSource().drop(pos);
        target.queueLightUpdate(() -> {
            var light = target.getLightEngine();
            light.setLightEnabled(pos, false);
            for (int y = light.getMinLightSection(); y < light.getMaxLightSection(); y++) {
                var section = SectionPos.of(pos, y);
                light.queueSectionData(LightLayer.BLOCK, section, null);
                light.queueSectionData(LightLayer.SKY, section, null);
            }
            for (int y = target.getMinSection(); y < target.getMaxSection(); y++) {
                light.updateSectionStatus(SectionPos.of(pos, y), true);
            }
        });
    }

    private static void applyWeather(MirrorLevel target, ClientboundGameEventPacket weather) {
        var type = weather.getEvent();
        if (type == ClientboundGameEventPacket.START_RAINING) {
            target.getLevelData().setRaining(true);
            target.setRainLevel(0);
        } else if (type == ClientboundGameEventPacket.STOP_RAINING) {
            target.getLevelData().setRaining(false);
            target.setRainLevel(1);
        } else if (type == ClientboundGameEventPacket.RAIN_LEVEL_CHANGE) {
            target.setRainLevel(weather.getParam());
        } else if (type == ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE) {
            target.setThunderLevel(weather.getParam());
        }
    }

    private static final class MirrorLevel extends ClientLevel {
        private final ClientLevel source;

        private MirrorLevel(ClientLevel source, ClientLevelData data, LevelRenderer renderer, int radius) {
            super(Minecraft.getInstance().getConnection(), data, source.dimension(), source.dimensionTypeRegistration(),
                    radius, source.getServerSimulationDistance(), Minecraft.getInstance()::getProfiler,
                    renderer, source.isDebug(), ((RealityBiomeAccessor)source.getBiomeManager()).stillworld$biomeZoomSeed());
            this.source = source;
        }

        @Override
        public Iterable<Entity> entitiesForRendering() { return source.entitiesForRendering(); }

        private void tickVisualBlockEntities() { super.tickBlockEntities(); }

        // Visual copies may update lids, pistons and other local animation state, never global effects.
        @Override
        public void tickEntities() { tickVisualBlockEntities(); }

        @Override
        public void sendPacketToServer(Packet<?> packet) { }

        @Override
        public void playSeededSound(@Nullable Player player, double x, double y, double z,
                Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed) { }

        @Override
        public void playSeededSound(@Nullable Player player, Entity entity, Holder<SoundEvent> sound,
                SoundSource category, float volume, float pitch, long seed) { }

        @Override
        public void playLocalSound(Entity entity, SoundEvent sound, SoundSource category, float volume, float pitch) { }

        @Override
        public void playLocalSound(double x, double y, double z, SoundEvent sound, SoundSource category,
                float volume, float pitch, boolean distanceDelay) { }

        @Override
        public void globalLevelEvent(int type, BlockPos pos, int data) { }

        @Override
        public void levelEvent(@Nullable Player player, int type, BlockPos pos, int data) { }

        @Override
        public void addParticle(ParticleOptions type, double x, double y, double z, double dx, double dy, double dz) { }

        @Override
        public void addParticle(ParticleOptions type, boolean force, double x, double y, double z,
                double dx, double dy, double dz) { }

        @Override
        public void addAlwaysVisibleParticle(ParticleOptions type, double x, double y, double z,
                double dx, double dy, double dz) { }

        @Override
        public void addAlwaysVisibleParticle(ParticleOptions type, boolean force, double x, double y, double z,
                double dx, double dy, double dz) { }
    }
}
