package dev.stillworld.client;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;

/** Exercise the actual mirror cache and light handler without production test hooks. */
final class MirrorCacheChecks {
    static void check() throws ReflectiveOperationException {
        var mc = Minecraft.getInstance();
        var level = RealityWorld.level();
        require(level != null && mc.player != null, "Mirror cache fixture has no active world");
        var sessionField = RealityWorld.class.getDeclaredField("session");
        sessionField.setAccessible(true);
        var session = (UUID) sessionField.get(null);
        var center = mc.player.chunkPosition();
        var near = level.getChunkSource().getChunk(center.x, center.z, false);
        require(near != null, "Initial mirror seed omitted the current chunk");
        var packet = new ClientboundLevelChunkWithLightPacket(near, level.getLightEngine(), null, null);
        require(!packet.getLightData().getSkyYMask().isEmpty()
                || !packet.getLightData().getBlockYMask().isEmpty(), "Mirror light fixture contains no section data");
        int initialSlots = slots(level);
        int initialCount = level.getChunkSource().getLoadedChunksCount();
        var far = new ChunkPos(center.x + 62_503, center.z);
        RealityWorld.receive(session, at(level, packet, far));
        RealityWorld.receive(session, lightAt(packet, far));
        level.pollLightUpdates();
        level.getLightEngine().runLightUpdates();
        require(slots(level) == initialSlots, "A distant packet expanded the mirror cache");
        require(level.getChunkSource().getLoadedChunksCount() == initialCount
                && level.getChunkSource().getChunk(center.x, center.z, false) == near,
                "A distant packet displaced ordinary mirror terrain");
        require(level.getChunkSource().getChunk(far.x, far.z, false) == null,
                "A stale distant chunk was accepted by the mirror");
        for (int y = level.getLightEngine().getMinLightSection(); y < level.getLightEngine().getMaxLightSection(); y++) {
            for (var layer : LightLayer.values()) {
                require(level.getLightEngine().getLayerListener(layer).getDataLayerData(SectionPos.of(far, y)) == null,
                        "A stale distant packet retained mirror light data");
            }
        }

        int originalDistance = mc.options.renderDistance().get();
        int originalEffective = mc.options.getEffectiveRenderDistance();
        var serverDistanceField = Options.class.getDeclaredField("serverRenderDistance");
        serverDistanceField.setAccessible(true);
        int originalServerDistance = serverDistanceField.getInt(mc.options);
        int larger = originalEffective + 2;
        require(larger <= 32, "Mirror resize fixture needs room to grow the configured distance");
        var edge = new ChunkPos(center.x + originalEffective + 4, center.z);
        try {
            mc.options.setServerRenderDistance(larger);
            mc.options.renderDistance().set(larger);
            RealityWorld.tick();
            require(slots(level) > initialSlots, "Increasing effective view distance did not grow the mirror cache");
            RealityWorld.receive(session, at(level, packet, edge));
            require(level.getChunkSource().getChunk(edge.x, edge.z, false) != null,
                    "A newly visible chunk was rejected after increasing view distance");

            // Shrink through the network cap while leaving the local preference larger.
            mc.options.setServerRenderDistance(originalEffective);
            RealityWorld.tick();
            require(slots(level) == initialSlots, "Decreasing network view distance did not shrink the mirror cache");
            require(level.getChunkSource().getChunk(edge.x, edge.z, false) == null,
                    "A chunk beyond the reduced view remained addressable");
            require(level.getChunkSource().getChunk(center.x, center.z, false) == near,
                    "View-distance changes lost the current mirror chunk");

            mc.options.setServerRenderDistance(larger);
            RealityWorld.receive(session, at(level, packet, edge));
            require(level.getChunkSource().getChunk(edge.x, edge.z, false) != null,
                    "A returning chunk was rejected before the next client tick");
            RealityWorld.receive(session, new ClientboundForgetLevelChunkPacket(edge));
            level.pollLightUpdates();
            level.getLightEngine().runLightUpdates();
        } finally {
            mc.options.setServerRenderDistance(originalServerDistance);
            mc.options.renderDistance().set(originalDistance);
            RealityWorld.tick();
        }
        require(slots(level) == initialSlots, "Restoring view settings left an expanded mirror cache");
        System.out.println("STILLWORLD MIRROR CACHE: stale distance=62503, slots=" + initialSlots
                + ", near terrain retained, settings grow/shrink and returning chunk accepted");
    }

    private static ClientboundLevelChunkWithLightPacket at(ClientLevel level,
            ClientboundLevelChunkWithLightPacket packet, ChunkPos pos) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            buffer.writeInt(pos.x); buffer.writeInt(pos.z);
            packet.getChunkData().write(buffer); packet.getLightData().write(buffer);
            return ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(buffer);
        } finally { buffer.release(); }
    }

    private static ClientboundLightUpdatePacket lightAt(ClientboundLevelChunkWithLightPacket packet, ChunkPos pos) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(pos.x); buffer.writeVarInt(pos.z); packet.getLightData().write(buffer);
            return ClientboundLightUpdatePacket.STREAM_CODEC.decode(buffer);
        } finally { buffer.release(); }
    }

    private static int slots(ClientLevel level) {
        return Integer.parseInt(level.getChunkSource().gatherStats().split(",")[0].trim());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private MirrorCacheChecks() { }
}
