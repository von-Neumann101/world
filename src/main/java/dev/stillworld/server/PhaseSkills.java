package dev.stillworld.server;
import dev.stillworld.ModContent;
import dev.stillworld.core.Slash;
import dev.stillworld.network.PhaseNetwork;
import dev.stillworld.snapshot.SnapshotReadContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import java.util.*;

public final class PhaseSkills {
    private static final Map<UUID,PhaseSession> LOCKS=new HashMap<>();
    private static final TicketType<UUID> MARK_TICKET=TicketType.create("stillworld_mark",Comparator.comparing(UUID::toString));
    public static void register() {
        PhaseManager.actionHandler=PhaseSkills::handle;PhaseManager.tickHandler=PhaseSkills::tick;PhaseManager.finishHandler=PhaseSkills::finish;
        NeoForge.EVENT_BUS.addListener(PhaseSkills::freezeTick);
        NeoForge.EVENT_BUS.addListener(PhaseSkills::removed);
        NeoForge.EVENT_BUS.addListener(PhaseSkills::recover);
        NeoForge.EVENT_BUS.addListener(PhaseSkills::startTracking);
    }
    private static void handle(PhaseSession s,CompoundTag data) {
        switch(data.getString("op")) {
            case "mark" -> { if(data.hasUUID("target"))mark(s,data.getUUID("target")); }
            case "attack" -> { if(data.hasUUID("target"))attack(s,data.getUUID("target")); }
            case "start_break" -> {
                BlockPos pos=BlockPos.of(data.getLong("pos"));
                if((s.rank.canBreak()||s.placedBefore.containsKey(pos))&&canReachBlock(s,pos)) {s.mining=pos;s.miningProgress=0;}
            }
            case "stop_break" -> stopMining(s);
            case "place" -> {
                int face=data.getInt("face"),hand=data.getInt("hand");
                if(face>=0&&face<6&&hand>=0&&hand<2)place(s,BlockPos.of(data.getLong("pos")),Direction.values()[face],InteractionHand.values()[hand],new Vec3(data.getDouble("hitX"),data.getDouble("hitY"),data.getDouble("hitZ")));
            }
            case "slash" -> slash(s);
            case "capture_pose" -> capturePose(s,data);
            default -> { }
        }
    }
    private static boolean canReachBlock(PhaseSession s,BlockPos pos) {
        var eye=s.player.getEyePosition();double reach=s.player.blockInteractionRange()+0.25;
        var hit=s.snapshot().clip(new ClipContext(eye,eye.add(s.player.getLookAngle().scale(reach)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,s.player));
        return hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos);
    }
    private static boolean mayEdit(PhaseSession s,BlockPos pos) {
        return s.player.mayBuild()&&!s.level.isOutsideBuildHeight(pos)&&s.level.getWorldBorder().isWithinBounds(pos)&&s.level.mayInteract(s.player,pos);
    }
    public static boolean place(PhaseSession s,BlockPos clicked,Direction face,InteractionHand hand,Vec3 hit) {
        if(!s.rank.canPlace()||!mayEdit(s,clicked)||!canReachBlock(s,clicked)||!Double.isFinite(hit.x)||!Double.isFinite(hit.y)||!Double.isFinite(hit.z)||hit.distanceToSqr(Vec3.atCenterOf(clicked))>1.0)return false;
        var stack=s.player.getItemInHand(hand);if(!(stack.getItem() instanceof BlockItem item))return false;
        var states=new LinkedHashMap<BlockPos,BlockState>();
        boolean valid=SnapshotReadContext.with(s.snapshot(),()->{
            var context=new BlockPlaceContext(new UseOnContext(s.player,hand,new BlockHitResult(hit,face,clicked,false)));
            var state=item.getBlock().getStateForPlacement(context);var pos=context.getClickedPos();
            if(state==null||!context.canPlace()||!state.canSurvive(s.level,pos))return false;
            states.put(pos.immutable(),state);
            if(state.getBlock() instanceof DoorBlock||state.getBlock() instanceof DoublePlantBlock) states.put(pos.above(),state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER));
            if(state.getBlock() instanceof BedBlock) states.put(pos.relative(state.getValue(BedBlock.FACING)),state.setValue(BedBlock.PART,BedPart.HEAD));
            return true;
        });
        if(!valid)return false;
        var recovery=FogRecovery.get(s.level);var originals=new LinkedHashMap<BlockPos,BlockState>();
        for(var entry:states.entrySet()) {
            var pos=entry.getKey();var old=s.snapshot().getBlockState(pos);
            if(!mayEdit(s,pos)||!old.canBeReplaced()||!s.level.getBlockState(pos).canBeReplaced()||recovery.occupied(pos))return false;
            var shape=entry.getValue().getCollisionShape(s.snapshot(),pos,CollisionContext.of(s.player));
            if(!shape.isEmpty()&&shape.move(pos.getX(),pos.getY(),pos.getZ()).toAabbs().stream().anyMatch(s.player.getBoundingBox()::intersects))return false;
            originals.put(pos,old);
        }
        var rollback=new ArrayList<BlockSnapshot>();
        for(var pos:states.keySet()) {
            var before=BlockSnapshot.create(s.level.dimension(),s.level,pos);rollback.add(before);
            if(!s.level.setBlock(pos,ModContent.BLACK_MIST.get().defaultBlockState(),3)||NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(before,s.level.getBlockState(clicked),s.player)).isCanceled()) {
                for(int i=rollback.size()-1;i>=0;i--)rollback.get(i).restore();return false;
            }
        }
        states.forEach((pos,state)->{s.placedBefore.put(pos,originals.get(pos));recovery.track(pos,s.id,false);edit(s,pos,state,true);});
        if(!s.player.isCreative())stack.shrink(1);
        return true;
    }
    public static boolean breakBlock(PhaseSession s,BlockPos pos) {
        if(!mayEdit(s,pos)||!canReachBlock(s,pos))return false;
        if(s.placedBefore.containsKey(pos)) {
            var before=s.placedBefore.remove(pos);FogRecovery.get(s.level).remove(s.level,pos,s.id);edit(s,pos,before,false);return true;
        }
        if(!s.rank.canBreak())return false;
        var original=s.snapshot().getBlockState(pos);var real=s.level.getBlockState(pos);
        if(original.isAir()||original.getDestroySpeed(s.snapshot(),pos)<0||real!=original||FogRecovery.get(s.level).occupied(pos)) {
            if(real!=original)message(s,"changed_block");return false;
        }
        if(NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(s.level,pos,real,s.player)).isCanceled())return false;
        var entity=s.level.getBlockEntity(pos);var tool=s.player.getMainHandItem();
        if(!s.player.isCreative()&&s.player.hasCorrectToolForDrops(real))Block.dropResources(real,s.level,pos,entity,s.player,tool);
        if(!s.player.isCreative())tool.mineBlock(s.level,real,pos,s.player);
        if(!s.level.setBlock(pos,ModContent.GRAY_MIST.get().defaultBlockState(),3))return false;
        FogRecovery.get(s.level).track(pos,s.id,true);edit(s,pos,Blocks.AIR.defaultBlockState(),false);return true;
    }
    private static void edit(PhaseSession s,BlockPos pos,BlockState state,boolean placed) {
        s.snapshot().setBlockState(pos,state);var data=PhaseNetwork.message("block");data.putUUID("session",s.id);data.putLong("pos",pos.asLong());data.putInt("state",Block.getId(state));data.putBoolean("placed",placed);PhaseNetwork.sendState(s.player,data);
    }
    private static AABB actorBox(CompoundTag visual) {
        var data=visual.getCompound("entity").getList("Pos",Tag.TAG_DOUBLE);double x=data.getDouble(0),y=data.getDouble(1),z=data.getDouble(2),width=visual.getFloat("width")/2;
        return new AABB(x-width,y,z-width,x+width,y+visual.getFloat("height"),z+width);
    }
    private static boolean sees(PhaseSession s,UUID target,double reach) {
        var visual=s.actors.get(target);if(visual==null||s.dead.contains(target))return false;
        var eye=s.player.getEyePosition();var end=eye.add(s.player.getLookAngle().scale(reach));var hit=actorBox(visual).inflate(0.1).clip(eye,end);
        if(hit.isEmpty())return false;
        var block=s.snapshot().clip(new ClipContext(eye,hit.get(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,s.player));
        return block.getType()==HitResult.Type.MISS||block.getLocation().distanceToSqr(eye)+0.01>=hit.get().distanceToSqr(eye);
    }
    public static boolean mark(PhaseSession s,UUID id) {
        if(!s.rank.canMark()||!sees(s,id,32))return false;
        if(s.marked.containsKey(id))return true;
        if(LOCKS.containsKey(id)){message(s,"already_marked");return false;}
        if(!(s.level.getEntity(id) instanceof LivingEntity target)||target instanceof Player||!target.isAlive())return false;
        if(target instanceof EnderDragon||target.isPassenger()||target.isVehicle()||(target instanceof Mob mob&&mob.isLeashed())){message(s,"linked_target");return false;}
        if(!PsycheManager.canSpend(s,20))return false;
        var snapshot=new EntitySnapshot(s.actors.get(id));snapshot.restoreAndFreeze(target);s.marked.put(id,snapshot);LOCKS.put(id,s);
        s.level.getChunkSource().addRegionTicket(MARK_TICKET,new ChunkPos(BlockPos.containing(snapshot.position)),2,id);
        var data=PhaseNetwork.message("freeze");data.putUUID("uuid",id);data.put("visual",snapshot.visual);broadcast(s.level,data);
        PsycheManager.spend(s,20);
        return true;
    }
    public static boolean attack(PhaseSession s,UUID id) {
        if(!s.rank.canMark()||!s.marked.containsKey(id)||!sees(s,id,s.player.entityInteractionRange()+0.25))return false;
        if(!(s.level.getEntity(id) instanceof LivingEntity target)||!target.isAlive())return false;
        var player=s.player;float base=(float)player.getAttributeValue(Attributes.ATTACK_DAMAGE);float charge=player.getAttackStrengthScale(0.5f);
        var source=player.damageSources().playerAttack(player);var weapon=player.getWeaponItem();
        float enchantment=EnchantmentHelper.modifyDamage(s.level,weapon,target,source,base)-base;
        float damage=base*(0.2f+charge*charge*0.8f);
        damage+=weapon.getItem().getAttackDamageBonus(target,damage,source);
        boolean critical=charge>0.9f&&player.fallDistance>0&&!player.onGround()&&!player.onClimbable()&&!player.isInWater()&&!player.hasEffect(MobEffects.BLINDNESS)&&!player.isPassenger()&&!player.isSprinting();
        if(critical)damage*=1.5f;damage+=enchantment*charge;
        if(!Float.isFinite(damage)||damage<=0)return false;
        if(!PsycheManager.canSpend(s,4))return false;
        boolean accepted;
        if(s.rank.defersDamage()) {
            s.damage.add(id,damage);accepted=true;
        } else accepted=target.hurt(source,damage*0.8f);
        if(accepted)PsycheManager.spend(s,4);
        if(accepted&&!weapon.isEmpty()&&!player.isCreative()) {
            // The phase permits damage recording, not arbitrary real-world hit
            // callbacks (e.g. the mace's area launch). Preserve vanilla wear.
            var item=weapon.getItem();
            int wear=item instanceof DiggerItem?2:(item instanceof SwordItem||item instanceof MaceItem||item instanceof TridentItem?1:0);
            if(wear>0)weapon.hurtAndBreak(wear,player,EquipmentSlot.MAINHAND);
        }
        player.resetAttackStrengthTicker();
        if(!target.isAlive())dead(s,id);
        return accepted;
    }
    private static void slash(PhaseSession s) {
        if(!s.rank.canSlash()||!s.player.getMainHandItem().is(ModContent.BLADE.get()))return;
        if(s.slashes.size()>=16){message(s,"slash_limit");return;}
        if(!PsycheManager.canSpend(s,40))return;
        var center=s.player.getEyePosition().add(s.player.getLookAngle().scale(2));var slash=new Slash(center.x,center.y,center.z,s.player.getYRot(),s.player.getXRot());s.slashes.add(slash);
        var data=PhaseNetwork.message("slash");data.putUUID("session",s.id);data.putDouble("cx",center.x);data.putDouble("cy",center.y);data.putDouble("cz",center.z);data.putFloat("yaw",slash.yaw());data.putFloat("pitch",slash.pitch());PhaseNetwork.sendState(s.player,data);
        PsycheManager.spend(s,40);
    }
    private static void tick(PhaseSession s) {
        if(s.mining!=null) {
            if(!canReachBlock(s,s.mining)){stopMining(s);}else {
                var state=s.snapshot().getBlockState(s.mining);
                float increment=s.player.isCreative()?1:SnapshotReadContext.with(s.snapshot(),()->state.getDestroyProgress(s.player,s.level,s.mining));
                if(state.isAir()||increment<=0||!Float.isFinite(increment))stopMining(s);
                else {s.miningProgress+=increment;if(s.miningProgress>=1){var pos=s.mining;stopMining(s);breakBlock(s,pos);}else crack(s,(int)(s.miningProgress*10));}
            }
        }
        for(var id:List.copyOf(s.actors.keySet())) {
            var entity=s.level.getEntity(id);if(entity instanceof LivingEntity living&&!living.isAlive())dead(s,id);
        }
        if(s.level.getGameTime()%3==0)for(var id:s.marked.keySet()) {
            var target=s.level.getEntity(id);if(target!=null)s.level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,target.getX(),target.getY()+target.getBbHeight()/2,target.getZ(),3,0.4,0.6,0.4,0.01);
        }
    }
    private static void crack(PhaseSession s,int progress) { if(s.player.connection!=null&&s.mining!=null)s.player.connection.send(new ClientboundBlockDestructionPacket(s.player.getId(),s.mining,progress)); }
    private static void stopMining(PhaseSession s) {crack(s,-1);s.mining=null;s.miningProgress=0;}
    private static void freezeTick(EntityTickEvent.Pre event) {
        if(event.getEntity().level().isClientSide||!(event.getEntity() instanceof LivingEntity target)||!target.isAlive())return;
        var s=LOCKS.get(target.getUUID());if(s==null)return;var snapshot=s.marked.get(target.getUUID());if(snapshot==null)return;
        if(target.invulnerableTime>0)target.invulnerableTime--;
        snapshot.pin(target);event.setCanceled(true);
    }
    private static void removed(EntityLeaveLevelEvent event) {
        if(event.getEntity().getRemovalReason()!=Entity.RemovalReason.KILLED)return;
        for(var s:PhaseManager.sessions())if(s.level==event.getLevel())dead(s,event.getEntity().getUUID());
    }
    private static void dead(PhaseSession s,UUID id) {
        if(!s.actors.containsKey(id)||!s.dead.add(id))return;
        s.damage.discard(id);unlock(s,id);
        var data=PhaseNetwork.message("dead");data.putUUID("session",s.id);data.putUUID("uuid",id);PhaseNetwork.sendState(s.player,data);
    }
    private static void unlock(PhaseSession s,UUID id) {
        var snapshot=s.marked.remove(id);if(snapshot==null)return;
        LOCKS.remove(id,s);if(s.level.getEntity(id) instanceof LivingEntity target)snapshot.release(target);
        s.level.getChunkSource().removeRegionTicket(MARK_TICKET,new ChunkPos(BlockPos.containing(snapshot.position)),2,id);
        var data=PhaseNetwork.message("unfreeze");data.putUUID("uuid",id);broadcast(s.level,data);
    }
    private static void finish(PhaseSession s) {
        stopMining(s);
        try {
            for(var id:List.copyOf(s.marked.keySet())) {
                float amount=s.damage.drain(id);
                if(amount>0&&s.level.getEntity(id) instanceof LivingEntity target&&target.isAlive()) {target.invulnerableTime=0;target.hurt(s.player.damageSources().playerAttack(s.player),amount);}
            }
            for(var slash:s.slashes) {
                var region=new AABB(slash.x()-3,slash.y()-3,slash.z()-3,slash.x()+3,slash.y()+3,slash.z()+3);
                for(var target:s.level.getEntitiesOfClass(LivingEntity.class,region)) {
                    if(target==s.player||!target.isAlive()||PhaseManager.isPhased(target)||s.player.isAlliedTo(target))continue;
                    if(target instanceof Player other&&(!s.level.getServer().isPvpAllowed()||!s.player.canHarmPlayer(other)))continue;
                    Entity receiver=target;boolean hit=intersects(slash,target);
                    if(target.isMultipartEntity()&&target.getParts()!=null)for(var part:target.getParts())if(intersects(slash,part)){receiver=part;hit=true;break;}
                    if(hit){target.invulnerableTime=0;receiver.hurt(s.player.damageSources().playerAttack(s.player),target.getMaxHealth()*0.3f);}
                }
            }
        } finally {
            for(var id:List.copyOf(s.marked.keySet()))unlock(s,id);
            s.slashes.clear();FogRecovery.get(s.level).finish(s.level,s.id);s.placedBefore.clear();
        }
    }
    private static boolean intersects(Slash slash,Entity target) {var b=target.getBoundingBox();return slash.intersects(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
    private static void recover(ChunkEvent.Load event) {if(event.getLevel() instanceof ServerLevel level)FogRecovery.get(level).recoverChunk(level,event.getChunk().getPos());}
    private static void startTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer observer)) return;
        var id=event.getTarget().getUUID();var session=LOCKS.get(id);
        if(session==null||session.level!=observer.serverLevel())return;
        var snapshot=session.marked.get(id);if(snapshot==null)return;
        var data=PhaseNetwork.message("freeze");data.putUUID("uuid",id);data.put("visual",snapshot.visual);PhaseNetwork.sendState(observer,data);
    }
    private static void broadcast(ServerLevel level,CompoundTag data) {for(var player:level.players())PhaseNetwork.sendState(player,data);}
    private static void message(PhaseSession s,String key) {if(s.player.connection!=null)s.player.displayClientMessage(Component.translatable("message.stillworld."+key),true);}
    private static void capturePose(PhaseSession s,CompoundTag data) {
        if(!data.hasUUID("target")||!data.contains("clientPose",Tag.TAG_COMPOUND))return;
        UUID id=data.getUUID("target");var visual=s.actors.get(id);
        if(visual==null||visual.contains("clientPose")||s.marked.containsKey(id))return;
        var pose=data.getCompound("clientPose");
        if(pose.getInt("v")!=1||pose.sizeInBytes()>8192)return;
        var allowed=Set.of("v","tick","pose","crouch","bodyYaw","headYaw","attack","walkPosition","walkSpeed","swim","hurt","death","fallFly","useHand","useTicks","swingHand","elytraX","elytraY","elytraZ","model","values","animations");
        if(!allowed.containsAll(pose.getAllKeys()))return;
        var lengths=Map.ofEntries(Map.entry("",0),Map.entry("creeper",3),Map.entry("guardian",6),Map.entry("polar_bear",2),Map.entry("wolf",6),Map.entry("cat",6),Map.entry("horse",8),Map.entry("panda",7),Map.entry("fox",4),Map.entry("bee",2),Map.entry("rabbit",2),Map.entry("sheep",1),Map.entry("shulker",2),Map.entry("goat",2),Map.entry("warden",4),Map.entry("armadillo",2),Map.entry("slime",3),Map.entry("squid",8),Map.entry("chicken",4),Map.entry("parrot",4),Map.entry("dragon",197));
        if(!lengths.containsKey(pose.getString("model"))||pose.getIntArray("values").length!=lengths.get(pose.getString("model")))return;
        if(pose.getLongArray("animations").length>64||pose.getLongArray("animations").length%2!=0)return;
        for(String key:List.of("bodyYaw","headYaw","attack","walkPosition","walkSpeed","swim","elytraX","elytraY","elytraZ"))if(!Float.isFinite(pose.getFloat(key)))return;
        for(int value:pose.getIntArray("values"))if(!Float.isFinite(Float.intBitsToFloat(value)))return;
        visual.put("clientPose",pose.copy());
    }
}
