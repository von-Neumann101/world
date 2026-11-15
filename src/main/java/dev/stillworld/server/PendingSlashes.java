package dev.stillworld.server;

import dev.stillworld.core.Slash;
import dev.stillworld.network.PhaseNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Fixed real-world attacks with a lifetime independent of the owner's phase session. */
public final class PendingSlashes {
    public static final int DELAY_TICKS=30;
    private static final TicketType<UUID> TICKET=TicketType.create("stillworld_slash",Comparator.comparing(UUID::toString));
    private static final List<Pending> PENDING=new ArrayList<>();
    private record Pending(UUID id,UUID session,ServerPlayer owner,ServerLevel level,Slash shape,long due,ChunkPos chunk) {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PendingSlashes::tick);
        NeoForge.EVENT_BUS.addListener(PendingSlashes::stop);
    }
    public static UUID schedule(PhaseSession session,Slash shape) {
        UUID id=UUID.randomUUID();var chunk=new ChunkPos(BlockPos.containing(shape.x(),shape.y(),shape.z()));
        // Keep the narrow hit region and adjacent entity positions ticking during the short windup.
        session.level.getChunkSource().addRegionTicket(TICKET,chunk,3,id);
        PENDING.add(new Pending(id,session.id,session.player,session.level,shape,session.level.getGameTime()+DELAY_TICKS,chunk));
        session.slashes.put(id,shape);return id;
    }
    private static void tick(ServerTickEvent.Post event) {
        var due=new ArrayList<Pending>();
        for(var iterator=PENDING.iterator();iterator.hasNext();) {
            var pending=iterator.next();
            if(pending.level.getServer()==event.getServer()&&pending.level.getGameTime()>=pending.due) {
                iterator.remove();due.add(pending);
            }
        }
        for(var pending:due) {
            try {hit(pending);} finally {
                release(pending);
                var current=pending.level.getServer().getPlayerList().getPlayer(pending.owner.getUUID());
                if(current==null)current=pending.owner;
                var session=PhaseManager.get(current);
                if(session!=null&&session.id.equals(pending.session)) {
                    session.slashes.remove(pending.id);
                    var data=PhaseNetwork.message("slash_end");data.putUUID("session",pending.session);data.putUUID("id",pending.id);
                    PhaseNetwork.sendState(current,data);
                }
            }
        }
    }
    private static void hit(Pending pending) {
        var level=pending.level;var slash=pending.shape;
        var owner=level.getServer().getPlayerList().getPlayer(pending.owner.getUUID());
        if(owner==null)owner=pending.owner;
        var region=new AABB(slash.x()-3,slash.y()-3,slash.z()-3,slash.x()+3,slash.y()+3,slash.z()+3);
        for(var target:level.getEntitiesOfClass(LivingEntity.class,region)) {
            if(target.getUUID().equals(owner.getUUID())||!target.isAlive()||PhaseManager.isPhased(target)||owner.isAlliedTo(target))continue;
            if(target instanceof Player other&&(!level.getServer().isPvpAllowed()||!owner.canHarmPlayer(other)))continue;
            Entity receiver=target;boolean hit=intersects(slash,target);
            if(target.isMultipartEntity()&&target.getParts()!=null)for(var part:target.getParts())if(intersects(slash,part)){receiver=part;hit=true;break;}
            if(hit) {
                target.invulnerableTime=0;
                receiver.hurt(level.damageSources().playerAttack(owner),target.getMaxHealth()*.3f);
            }
        }
    }
    private static boolean intersects(Slash slash,Entity target) {
        var b=target.getBoundingBox();return slash.intersects(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);
    }
    private static void release(Pending pending) {pending.level.getChunkSource().removeRegionTicket(TICKET,pending.chunk,3,pending.id);}
    private static void stop(ServerStoppingEvent event) {
        PENDING.removeIf(pending->{if(pending.level.getServer()!=event.getServer())return false;release(pending);return true;});
    }
    private PendingSlashes(){}
}
