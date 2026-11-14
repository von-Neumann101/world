package dev.stillworld.server;
import dev.stillworld.mixin.CreeperSnapshotAccessor;
import net.minecraft.nbt.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

public final class EntitySnapshot {
    public final CompoundTag visual;
    public final Vec3 position;
    public final Vec3 velocity;
    private final float yaw,pitch;
    private final Pose pose;
    public EntitySnapshot(CompoundTag visual) {
        this.visual=visual.copy();var data=visual.getCompound("entity");var pos=data.getList("Pos",Tag.TAG_DOUBLE);var motion=data.getList("Motion",Tag.TAG_DOUBLE);var rotation=data.getList("Rotation",Tag.TAG_FLOAT);
        position=new Vec3(pos.getDouble(0),pos.getDouble(1),pos.getDouble(2));velocity=new Vec3(motion.getDouble(0),motion.getDouble(1),motion.getDouble(2));yaw=rotation.getFloat(0);pitch=rotation.getFloat(1);
        Pose parsed;try{parsed=Pose.valueOf(visual.getString("pose"));}catch(IllegalArgumentException e){parsed=Pose.STANDING;}pose=parsed;
    }
    public void restoreAndFreeze(LivingEntity target) {
        var data=visual.getCompound("entity");
        target.removeAllEffects();var effects=data.getList("active_effects",Tag.TAG_COMPOUND);
        for(int i=0;i<effects.size();i++){var effect=MobEffectInstance.load(effects.getCompound(i));if(effect!=null)target.addEffect(effect);}
        target.setHealth(data.getFloat("Health"));target.setAbsorptionAmount(data.getFloat("AbsorptionAmount"));
        target.setRemainingFireTicks(data.getShort("Fire"));target.setAirSupply(data.getShort("Air"));target.fallDistance=data.getFloat("FallDistance");
        if(target instanceof Mob mob){mob.getNavigation().stop();mob.setAggressive(visual.getBoolean("aggressive"));}
        // Restore intrinsic timers once at marking; pin must not reload state
        // over damage received while frozen or touch equipment and relationships.
        if (target instanceof Creeper creeper && visual.contains("creeper", Tag.TAG_COMPOUND)) {
            var access = (CreeperSnapshotAccessor) creeper;
            var fuse = visual.getCompound("creeper");
            access.stillworld$swell(fuse.getInt("swell"));
            access.stillworld$oldSwell(fuse.getInt("oldSwell"));
            access.stillworld$maxSwell(fuse.getInt("maxSwell"));
            access.stillworld$explosionRadius(fuse.getInt("explosionRadius"));
            creeper.setSwellDir(fuse.getInt("swellDir"));
            creeper.getEntityData().set(CreeperSnapshotAccessor.stillworld$ignitedData(), fuse.getBoolean("ignited"));
            creeper.getEntityData().set(CreeperSnapshotAccessor.stillworld$poweredData(), fuse.getBoolean("powered"));
        }
        if (target instanceof WitherBoss wither) wither.setInvulnerableTicks(data.getInt("Invul"));
        target.invulnerableTime=0;
        pin(target);
    }
    public void pin(LivingEntity target) {
        target.moveTo(position.x,position.y,position.z,yaw,pitch);
        target.xo=position.x;target.yo=position.y;target.zo=position.z;target.yRotO=yaw;target.xRotO=pitch;
        target.yBodyRot=target.yBodyRotO=visual.getFloat("bodyYaw");target.yHeadRot=target.yHeadRotO=visual.getFloat("headYaw");
        target.attackAnim=target.oAttackAnim=visual.getFloat("attack");target.hurtTime=visual.getInt("hurt");target.setPose(pose);target.setDeltaMovement(Vec3.ZERO);
    }
    public void release(LivingEntity target) { if(target.isAlive())target.setDeltaMovement(velocity); }
}
