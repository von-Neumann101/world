package dev.stillworld.client;

import dev.stillworld.network.PhaseNetwork;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Capture the actual client state just before the production begin handler. */
final class DroppedItemPoseChecks {
    private record Pose(ItemEntity live,int age,float phase,float spin,float bob,ItemStack stack) {}
    private final List<UUID> ids=new ArrayList<>();
    private final Map<UUID,Pose> entry=new LinkedHashMap<>();

    static DroppedItemPoseChecks spawn(ServerLevel level) {
        var checks=new DroppedItemPoseChecks();
        var stacks=List.of(new ItemStack(Items.DIAMOND_SWORD),new ItemStack(Items.OAK_LOG,32));
        for(int i=0;i<stacks.size();i++) {
            var item=new ItemEntity(level,i==0?-2.5:2.5,100.7,1.5,stacks.get(i),0,0,0);
            item.setNoGravity(true);item.setNeverPickUp();item.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(item);checks.ids.add(item.getUUID());
        }
        return checks;
    }
    void arm() {
        var previous=PhaseNetwork.clientHandler;
        PhaseNetwork.clientHandler=tag->{
            if(tag.getString("op").equals("begin")) {
                PhaseNetwork.clientHandler=previous;
                for(var entity:Minecraft.getInstance().level.entitiesForRendering()) {
                    if(entity instanceof ItemEntity item&&ids.contains(item.getUUID()))
                        entry.put(item.getUUID(),new Pose(item,item.getAge(),item.bobOffs,item.getSpin(1),bob(item),item.getItem().copy()));
                }
                require(entry.size()==2,"Both dropped-item fixtures must reach the client before entry");
            }
            previous.accept(tag);
        };
    }
    void check() {
        require(entry.size()==2,"Entry item state was not captured");
        for(var sample:entry.entrySet()) {
            var pose=sample.getValue();
            require(StillworldClient.ghosts().get(sample.getKey()) instanceof ItemEntity,"Dropped item missing from snapshot");
            var ghost=(ItemEntity)StillworldClient.ghosts().get(sample.getKey());
            require(Math.abs(ghost.getSpin(1)-pose.spin)<.000001f,"Entry changed dropped-item direction: expected "+pose.spin+" got "+ghost.getSpin(1));
            require(Math.abs(bob(ghost)-pose.bob)<.000001f,"Entry changed dropped-item bob height");
            require(ghost.getAge()==pose.age,"Frozen dropped-item animation advanced");
            require(ghost.getItem().getCount()==pose.stack.getCount()&&ItemStack.isSameItemSameComponents(ghost.getItem(),pose.stack),"Visual copy changed item contents");
            require(pose.live.getAge()>pose.age+20&&pose.live.bobOffs==pose.phase,"Snapshot altered or stopped the live dropped item");
            require(ghost!=pose.live,"Snapshot must remain an independent dropped item");
        }
        System.out.println("STILLWORLD DROPPED ITEM POSE PASS: flat/block items retain entry spin and bob, frozen age and live animation remain independent");
    }
    private static float bob(ItemEntity item){return Mth.sin((item.getAge()+1)/10f+item.bobOffs)*.1f+.1f;}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
