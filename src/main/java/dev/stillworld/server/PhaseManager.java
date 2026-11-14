package dev.stillworld.server;

import dev.stillworld.ModContent;
import dev.stillworld.mixin.ChunkMapAccessor;
import dev.stillworld.network.PhaseNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class PhaseManager {
    private static final Map<UUID, PhaseSession> SESSIONS = new java.util.concurrent.ConcurrentHashMap<>();
    public static Consumer<PhaseSession> finishHandler = session -> {};
    public static Consumer<PhaseSession> tickHandler = session -> {};
    public static BiConsumer<PhaseSession, CompoundTag> actionHandler = (session, action) -> {};
    public static Collection<PhaseSession> sessions() { return List.copyOf(SESSIONS.values()); }
    public static PhaseSession get(Player player) { return SESSIONS.get(player.getUUID()); }
    public static boolean isPhased(Entity entity) {
        return entity.level().isClientSide ? PhaseNetwork.clientPhased.test(entity) : SESSIONS.containsKey(entity.getUUID());
    }
    public static void register() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(PhaseManager::chunkLoaded);
        bus.addListener(PhaseManager::entityJoined);
        bus.addListener(PhaseManager::playerTick);
        bus.addListener(PhaseManager::logout);
        bus.addListener(PhaseManager::login);
        bus.addListener(PhaseManager::dimension);
        bus.addListener(PhaseManager::stop);
        bus.addListener(PhaseManager::damage);
        bus.addListener(PhaseManager::target);
    }
    public static boolean enter(ServerPlayer player) {
        var rank = ModContent.equippedRank(player);
        if (rank == null || get(player) != null || !player.isAlive() || player.isSpectator() || player.isPassenger() || player.isSleeping()) return false;
        var session = new PhaseSession(player, rank);
        for (var holder : ((ChunkMapAccessor)(Object) session.level.getChunkSource().chunkMap).stillworld$chunks()) {
            if (holder.getLatestChunk() instanceof LevelChunk chunk) session.snapshot().capture(chunk);
        }
        session.snapshot().capture(session.level.getChunkAt(player.blockPosition()));
        for (var entity : session.level.getAllEntities()) {
            if (entity != player && !isPhased(entity) && entity.isAlive()) session.actors.put(entity.getUUID(), EntityVisual.capture(entity));
            if (entity instanceof Mob mob && mob.getTarget() == player) mob.setTarget(null);
        }
        SESSIONS.put(player.getUUID(), session);
        for(var entity:session.level.getAllEntities())if(entity instanceof Mob mob) {
            var brain=mob.getBrain();
            if(brain.isMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET,player)) {
                brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
                brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
                brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);
                mob.getNavigation().stop();
            }
        }
        if (player.connection != null) player.closeContainer();
        player.stopUsingItem();
        player.setDeltaMovement(Vec3.ZERO);
        var begin = PhaseNetwork.message("begin");
        begin.putUUID("session", session.id); begin.putInt("rank", rank.ordinal());
        begin.putLong("dayTime", session.level.getDayTime()); begin.putLong("gameTime", session.entryTime);
        begin.putFloat("rain", session.level.getRainLevel(1)); begin.putFloat("thunder", session.level.getThunderLevel(1));
        PhaseNetwork.sendState(player, begin);
        for (var chunk : session.snapshot().chunks()) {
            var pos = chunk.chunk().getPos();
            if (player.connection != null && player.getChunkTrackingView().contains(pos.x, pos.z)) player.connection.send(chunk.packet());
        }
        session.actors.values().forEach(visual -> sendActor(session, visual));
        broadcast(player.getServer());
        return true;
    }
    private static void sendActor(PhaseSession session, CompoundTag visual) {
        var packet = PhaseNetwork.message("ghost"); packet.putUUID("session", session.id); packet.put("visual", visual);
        PhaseNetwork.sendState(session.player, packet);
    }
    public static void exit(ServerPlayer player) {
        var session = get(player);
        if (session == null || session.finishing) return;
        session.finishing = true;
        try { finishHandler.accept(session); }
        finally {
            SESSIONS.remove(player.getUUID());
            PhaseNetwork.sendState(player, PhaseNetwork.message("end"));
            if (player.connection != null) {
                for (var holder : ((ChunkMapAccessor)(Object) player.serverLevel().getChunkSource().chunkMap).stillworld$chunks()) {
                    if (holder.getLatestChunk() instanceof LevelChunk chunk && player.getChunkTrackingView().contains(chunk.getPos().x, chunk.getPos().z))
                        player.connection.send(new ClientboundLevelChunkWithLightPacket(chunk, player.serverLevel().getLightEngine(), null, null));
                }
                player.connection.send(new ClientboundSetTimePacket(player.serverLevel().getGameTime(), player.serverLevel().getDayTime(), player.serverLevel().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT)));
                player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, player.serverLevel().getRainLevel(1)));
                player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, player.serverLevel().getThunderLevel(1)));
            }
            if (player.isAlive() && player.serverLevel() == session.level) safeExit(player, session);
            broadcast(player.getServer());
        }
    }
    private static void safeExit(ServerPlayer player, PhaseSession session) {
        Vec3 target = player.position();
        if (!session.level.noCollision(player, player.getBoundingBox())) {
            Vec3 found = null;
            search: for (int radius = 1; radius <= 8; radius++) for (int y = 0; y <= radius; y++)
                for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                    var delta = new Vec3(x, y, z);
                    if (session.level.noCollision(player, player.getBoundingBox().move(delta))) { found = target.add(delta); break search; }
                }
            target = found == null ? session.entry : found;
        }
        player.fallDistance = 0;
        player.setDeltaMovement(Vec3.ZERO);
        if (player.connection != null) player.connection.teleport(target.x, target.y, target.z, player.getYRot(), player.getXRot());
        else player.setPos(target);
    }
    public static void handle(ServerPlayer player, CompoundTag data) {
        String op = data.getString("op");
        if (op.equals("toggle")) {
            if (get(player) != null) exit(player);
            else if (!enter(player)) player.displayClientMessage(Component.translatable("message.stillworld.need_charm"), true);
            return;
        }
        var session = get(player);
        if (session != null && !session.finishing && data.hasUUID("session") && session.id.equals(data.getUUID("session"))) actionHandler.accept(session, data);
    }
    public static Packet<?> replaceChunk(ServerPlayer player, Packet<?> original) {
        var session = get(player);
        if (session == null || !(original instanceof ClientboundLevelChunkWithLightPacket packet)) return original;
        var snapshot = session.snapshot().chunk(packet.getX(), packet.getZ());
        if (snapshot == null) {
            var chunk = session.level.getChunkSource().getChunkNow(packet.getX(), packet.getZ());
            if (chunk == null) return original;
            session.snapshot().capture(chunk);
            snapshot = session.snapshot().chunk(packet.getX(), packet.getZ());
        }
        return snapshot.packet();
    }
    public static boolean suppress(ServerPlayer player, Packet<?> packet) {
        if (get(player) == null) return false;
        if (packet instanceof ClientboundBlockUpdatePacket || packet instanceof ClientboundSectionBlocksUpdatePacket
                || packet instanceof ClientboundBlockEntityDataPacket || packet instanceof ClientboundBlockEventPacket
                || packet instanceof ClientboundLightUpdatePacket || packet instanceof ClientboundSetTimePacket
                || packet instanceof ClientboundLevelEventPacket || packet instanceof ClientboundLevelParticlesPacket
                || packet instanceof ClientboundSoundPacket || packet instanceof ClientboundSoundEntityPacket) return true;
        if (packet instanceof ClientboundGameEventPacket event) return event.getEvent() == ClientboundGameEventPacket.START_RAINING
                || event.getEvent() == ClientboundGameEventPacket.STOP_RAINING || event.getEvent() == ClientboundGameEventPacket.RAIN_LEVEL_CHANGE
                || event.getEvent() == ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE;
        return false;
    }
    public static void broadcast(MinecraftServer server) {
        var data = PhaseNetwork.message("phased"); var players = new ListTag();
        SESSIONS.keySet().forEach(id -> players.add(StringTag.valueOf(id.toString()))); data.put("players", players);
        for (var player : server.getPlayerList().getPlayers()) PhaseNetwork.sendState(player, data);
    }
    private static void chunkLoaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk)
            for (var session : sessions()) if (session.level == level) session.snapshot().capture(chunk);
    }
    private static void entityJoined(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        for (var session : sessions()) if (session.level == level && !session.actors.containsKey(event.getEntity().getUUID())) {
            if (!event.loadedFromDisk()) { session.laterEntities.add(event.getEntity().getUUID()); continue; }
            if (session.laterEntities.contains(event.getEntity().getUUID())) continue;
            var visual = EntityVisual.capture(event.getEntity()); session.actors.put(event.getEntity().getUUID(), visual); sendActor(session, visual);
        }
    }
    private static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var session = get(player); if (session == null) return;
        if (!player.isAlive() || ModContent.equippedRank(player) != session.rank || player.serverLevel() != session.level) { exit(player); return; }
        tickHandler.accept(session);
    }
    private static void damage(LivingIncomingDamageEvent event) { if (isPhased(event.getEntity())) event.setCanceled(true); }
    private static void target(LivingChangeTargetEvent event) { if (event.getNewAboutToBeSetTarget() != null && isPhased(event.getNewAboutToBeSetTarget())) event.setNewAboutToBeSetTarget(null); }
    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer player) exit(player); }
    private static void login(PlayerEvent.PlayerLoggedInEvent event) { if (event.getEntity() instanceof ServerPlayer player) broadcast(player.getServer()); }
    private static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer player) exit(player); }
    private static void stop(ServerStoppingEvent event) { for (var session : sessions()) exit(session.player); SESSIONS.clear(); }
    private PhaseManager() { }
}
