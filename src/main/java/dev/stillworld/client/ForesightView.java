package dev.stillworld.client;

import dev.stillworld.ModContent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Two-tick server predictions are interpolated without ever moving the real model or hitbox. */
public final class ForesightView {
    private record Echo(int entityId,Vec3 previous,Vec3 next,long tick) {
        Vec3 at(double now) { return previous.lerp(next,Math.clamp((now-tick)/2,0,1)); }
    }
    private static final Map<UUID,Echo> ECHOES=new HashMap<>();
    private static ClientLevel knownLevel;
    private static long ticks;

    public static void tick() {
        var mc=Minecraft.getInstance();
        if(knownLevel!=mc.level) {clear();knownLevel=mc.level;}
        if(mc.player==null||!ModContent.hasForesight(mc.player))ECHOES.clear();
        if(!mc.isPaused())ticks++;
        ECHOES.values().removeIf(e->ticks-e.tick>10);
    }
    public static void receive(CompoundTag packet) {
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||!ModContent.hasForesight(mc.player)
                ||!mc.level.dimension().location().toString().equals(packet.getString("dimension")))return;
        if(knownLevel!=mc.level) {clear();knownLevel=mc.level;}
        var next=new HashMap<UUID,Echo>();
        for(var entry:packet.getList("actors",Tag.TAG_COMPOUND)) {
            var data=(CompoundTag)entry;var id=data.getUUID("uuid");var entity=mc.level.getEntity(data.getInt("id"));
            if(entity==null||!entity.getUUID().equals(id))continue;
            Vec3 offset=new Vec3(data.getDouble("x"),data.getDouble("y"),data.getDouble("z"));
            var old=ECHOES.get(id);
            next.put(id,new Echo(entity.getId(),old==null?offset:old.at(ticks),offset,ticks));
        }
        ECHOES.clear();ECHOES.putAll(next);
    }
    public static boolean eligible(Entity entity) {
        var mc=Minecraft.getInstance();
        return mc.player!=null&&ModContent.hasForesight(mc.player)&&entity instanceof LivingEntity
                &&entity!=mc.player&&entity.isAlive()&&!entity.isInvisibleTo(mc.player)
                &&!StillworldClient.phased(entity)&&!StillworldClient.locked(entity)
                &&(!StillworldClient.active()||(StillworldClient.seeLive()&&StillworldClient.captured(entity.getUUID())&&!StillworldClient.isDead(entity.getUUID())));
    }
    @Nullable public static Vec3 offset(Entity entity,float partial) {
        var echo=ECHOES.get(entity.getUUID());
        return echo==null||echo.entityId!=entity.getId()||ticks-echo.tick>10?null:echo.at(ticks+partial);
    }
    public static void clear() { ECHOES.clear();knownLevel=null;ticks=0; }
    private ForesightView() { }
}
