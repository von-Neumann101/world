package dev.stillworld.server;

import dev.stillworld.ModContent;
import dev.stillworld.network.PhaseNetwork;
import java.util.IdentityHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Navigation belongs to the server; recipients receive only offsets for already tracked actors. */
public final class ForesightSync {
    private static final ForesightPrediction.Tracker TRACKER=new ForesightPrediction.Tracker();
    public static void register() {
        NeoForge.EVENT_BUS.addListener(ForesightSync::tick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent event)->TRACKER.clear());
    }
    private static void tick(ServerTickEvent.Post event) {
        if(event.getServer().getTickCount()%2!=0)return;
        var predictions=new IdentityHashMap<LivingEntity,Vec3>();
        for(var player:event.getServer().getPlayerList().getPlayers()) {
            if(!player.isAlive()||!ModContent.hasForesight(player))continue;
            var level=player.serverLevel();
            int view=event.getServer().getPlayerList().getViewDistance()*16;
            var actors=new ListTag();
            for(var entity:level.getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(view))) {
                if(entity==player||!entity.isAlive()||entity.isInvisibleTo(player)||PhaseManager.isPhased(entity)||PhaseSkills.isLocked(entity))continue;
                int range=Math.min(view,event.getServer().getScaledTrackingDistance(entity.getType().clientTrackingRange()*16));
                var chunk=entity.chunkPosition();
                if(!player.getChunkTrackingView().contains(chunk.x,chunk.z)
                        ||Math.abs(entity.getX()-player.getX())>range||Math.abs(entity.getZ()-player.getZ())>range)continue;
                Vec3 offset=predictions.computeIfAbsent(entity,e->TRACKER.predict(e,event.getServer().getTickCount()).subtract(e.position()));
                var actor=new CompoundTag();actor.putUUID("uuid",entity.getUUID());actor.putInt("id",entity.getId());
                actor.putDouble("x",offset.x);actor.putDouble("y",offset.y);actor.putDouble("z",offset.z);actors.add(actor);
            }
            var packet=PhaseNetwork.message("foresight");packet.putString("dimension",level.dimension().location().toString());packet.put("actors",actors);
            PhaseNetwork.sendState(player,packet);
        }
        TRACKER.retainTick(event.getServer().getTickCount());
    }
    private ForesightSync() { }
}
