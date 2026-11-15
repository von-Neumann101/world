package dev.stillworld.server;

import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Reads the current route only: no AI, movement, path advancement or world ticks are simulated. */
public final class ForesightPrediction {
    public static final int HORIZON_TICKS=30;

    public static Vec3 predict(LivingEntity entity,Vec3 motion) {
        Vec3 start=entity.position();
        // A teleport is not a velocity. A stopped or newly tracked actor stays at its real position.
        if(motion.lengthSqr()<1.0E-8||motion.lengthSqr()>16)return start;
        if(entity instanceof Mob mob&&!entity.isPassenger()) {
            var path=mob.getNavigation().getPath();
            if(path!=null&&!path.isDone()) {
                double budget=motion.length()*HORIZON_TICKS;
                Vec3 position=start;
                for(int i=path.getNextNodeIndex();i<path.getNodeCount()&&budget>1.0E-6;i++) {
                    Vec3 delta=path.getEntityPosAtNode(entity,i).subtract(position);
                    double length=delta.length();
                    if(length<1.0E-6)continue;
                    Vec3 step=delta.scale(Math.min(1,budget/length));
                    Vec3 next=move(entity,position,step);
                    if(next.subtract(position).distanceToSqr(step)>1.0E-6)return next;
                    position=next;budget-=length;
                }
                return position;
            }
        }
        return move(entity,start,motion.scale(HORIZON_TICKS));
    }

    /** Player packet movement precedes xOld resets, so the live sender samples across server ticks. */
    public static final class Tracker {
        private record Sample(Vec3 position,int tick) { }
        private final Map<LivingEntity,Sample> samples=new IdentityHashMap<>();
        public Vec3 predict(LivingEntity entity,int tick) {
            var old=samples.put(entity,new Sample(entity.position(),tick));
            int elapsed=old==null?0:tick-old.tick;
            Vec3 motion=elapsed<=0||elapsed>4?Vec3.ZERO:entity.position().subtract(old.position).scale(1.0/elapsed);
            return ForesightPrediction.predict(entity,motion);
        }
        public void retainTick(int tick) { samples.values().removeIf(sample->sample.tick!=tick); }
        public void clear() { samples.clear(); }
    }

    private static Vec3 move(LivingEntity entity,Vec3 position,Vec3 delta) {
        var level=entity.level();
        var box=entity.getBoundingBox().move(position.subtract(entity.position()));
        var swept=box.expandTowards(delta);
        if(!level.hasChunksAt(BlockPos.containing(swept.minX,swept.minY,swept.minZ),BlockPos.containing(swept.maxX,swept.maxY,swept.maxZ)))return position;
        Vec3 allowed=Entity.collideBoundingBox(entity,delta,box,level,List.of());
        return position.add(allowed);
    }

    private ForesightPrediction() { }
}
