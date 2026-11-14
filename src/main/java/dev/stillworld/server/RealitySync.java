package dev.stillworld.server;

import dev.stillworld.mixin.ChunkMapAccessor;
import dev.stillworld.network.RealityPayload;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RealitySync {
    public static void seed(PhaseSession session) {
        if (session.player.connection == null) return;
        // Use already loaded, tracked chunks only. The orb does not add chunk tickets.
        for (var holder : ((ChunkMapAccessor)(Object) session.level.getChunkSource().chunkMap).stillworld$chunks()) {
            if (holder.getLatestChunk() instanceof LevelChunk chunk
                    && session.player.getChunkTrackingView().contains(chunk.getPos().x, chunk.getPos().z)) {
                send(session, new ClientboundLevelChunkWithLightPacket(chunk, session.level.getLightEngine(), null, null));
            }
        }
        send(session, new ClientboundSetTimePacket(session.level.getGameTime(), session.level.getDayTime(),
                session.level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)));
        send(session, new ClientboundGameEventPacket(session.level.isRaining()
                ? ClientboundGameEventPacket.START_RAINING : ClientboundGameEventPacket.STOP_RAINING, 0));
        send(session, new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, session.level.getRainLevel(1)));
        send(session, new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, session.level.getThunderLevel(1)));
    }

    public static void forward(ServerPlayer player, Packet<?> packet) {
        var session = PhaseManager.get(player);
        if (session == null || session.finishing || player.connection == null || player.serverLevel() != session.level
                || !RealityPayload.supported(packet)) return;
        if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
            var snapshot = session.snapshot().chunk(chunk.getX(), chunk.getZ());
            // Explicit snapshot reloads must never overwrite the live mirror.
            if (snapshot != null && snapshot.packet() == packet) return;
        }
        send(session, packet);
    }

    private static void send(PhaseSession session, Packet<?> packet) {
        // Custom payloads are outside the allowlist, so this send cannot recurse.
        PacketDistributor.sendToPlayer(session.player, new RealityPayload(session.id, packet));
    }

    private RealitySync() { }
}
