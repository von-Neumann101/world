package dev.stillworld;

import dev.stillworld.core.Rank;
import dev.stillworld.network.StatePayload;
import dev.stillworld.server.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder(Stillworld.ID)
@PrefixGameTestTemplate(false)
public final class PairedBlockGameTests {
    @GameTest(template="empty") public static void capturedTreatmentLowerBreakClearsPair(GameTestHelper h) {
        breakPair(h,ModContent.TREATMENT_MACHINE.get(),false,Items.IRON_PICKAXE);
    }
    @GameTest(template="empty") public static void capturedTreatmentUpperBreakClearsPair(GameTestHelper h) {
        breakPair(h,ModContent.TREATMENT_MACHINE.get(),true,Items.IRON_PICKAXE);
    }
    @GameTest(template="empty") public static void capturedDoorLowerBreakClearsPair(GameTestHelper h) {
        breakPair(h,Blocks.OAK_DOOR,false,Items.IRON_AXE);
    }
    @GameTest(template="empty") public static void capturedDoorUpperBreakClearsPair(GameTestHelper h) {
        breakPair(h,Blocks.OAK_DOOR,true,Items.IRON_AXE);
    }
    @GameTest(template="empty") public static void capturedBedFootBreakClearsPair(GameTestHelper h) {
        breakPair(h,Blocks.RED_BED,false,Items.IRON_AXE);
    }
    @GameTest(template="empty") public static void capturedBedHeadBreakClearsPair(GameTestHelper h) {
        breakPair(h,Blocks.RED_BED,true,Items.IRON_AXE);
    }
    @GameTest(template="empty") public static void capturedPlantLowerBreakClearsPair(GameTestHelper h) {
        breakPair(h,Blocks.SUNFLOWER,false,Items.IRON_AXE);
    }
    @GameTest(template="empty") public static void capturedPlantUpperBreakClearsPair(GameTestHelper h) {
        breakPair(h,Blocks.SUNFLOWER,true,Items.IRON_AXE);
    }
    private static BlockPos pair(GameTestHelper h,BlockPos bottom,Block block) {
        h.getLevel().setBlock(bottom.below(),Blocks.DIRT.defaultBlockState(),2);
        var lower=block.defaultBlockState();
        var other=bottom.above();
        var upper=lower;
        if(block instanceof BedBlock) {
            lower=lower.setValue(BedBlock.FACING,Direction.EAST).setValue(BedBlock.PART,BedPart.FOOT);
            other=bottom.east();upper=lower.setValue(BedBlock.PART,BedPart.HEAD);
            h.getLevel().setBlock(other.below(),Blocks.DIRT.defaultBlockState(),2);
        } else {
            lower=lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.LOWER);
            upper=lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER);
        }
        h.getLevel().setBlock(bottom,lower,2);h.getLevel().setBlock(other,upper,2);
        return other;
    }
    private static void aimAt(PhaseSession s,BlockPos pos) {
        var center=s.snapshot().getBlockState(pos).getShape(s.snapshot(),pos).bounds().getCenter().add(Vec3.atLowerCornerOf(pos));
        // Beds need an overhead approach; vertical pairs need a side approach.
        s.player.setPos(s.snapshot().getBlockState(pos).getBlock() instanceof BedBlock?center.add(0,2,0):center.add(-2,-.6,0));
        SkillGameTests.aim(s.player,center);
    }
    private static int drops(GameTestHelper h,BlockPos bottom,Item item) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(bottom).inflate(2)).stream()
            .filter(e->e.getItem().is(item)).mapToInt(e->e.getItem().getCount()).sum();
    }
    private static void breakPair(GameTestHelper h,Block block,boolean second,Item tool) {
        var packets=new ArrayList<Packet<?>>();
        var p=SessionGameTests.player(h,packets::add);SessionGameTests.equip(p,Rank.RANK3);
        var bottom=p.blockPosition().east(2);var other=pair(h,bottom,block);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(tool));
        h.assertTrue(PhaseManager.enter(p),"fixture: entered phase");var s=PhaseManager.get(p);
        try {
            var target=second?other:bottom;var companion=second?bottom:other;
            int wear=s.snapshot().getBlockState(target).getDestroySpeed(s.snapshot(),target)>0?1:0;
            aimAt(s,target);packets.clear();
            h.assertTrue(PhaseSkills.breakBlock(s,target),"captured pair can be broken from either member");
            h.assertTrue(s.snapshot().getBlockState(bottom).isAir()&&s.snapshot().getBlockState(other).isAir(),"both frozen members removed");
            h.assertTrue(s.snapshot().getBlockState(companion).getCollisionShape(s.snapshot(),companion).isEmpty(),"no companion collision remains");
            h.assertTrue(h.getLevel().getBlockState(target).is(ModContent.GRAY_MIST.get())&&h.getLevel().getBlockState(companion).isAir(),"clicked member keeps gray mist and vanilla removes companion");
            var edits=new HashSet<BlockPos>();
            for(var packet:packets)if(packet instanceof ClientboundCustomPayloadPacket custom&&custom.payload() instanceof StatePayload state) {
                var tag=state.data();
                if(tag.getString("op").equals("block")&&tag.getInt("state")==Block.getId(Blocks.AIR.defaultBlockState()))edits.add(BlockPos.of(tag.getLong("pos")));
            }
            h.assertTrue(edits.containsAll(List.of(bottom,other)),"client receives removal for both members");
            h.assertTrue(drops(h,bottom,block.asItem())==1,"pair drops exactly one item");
            h.assertTrue(p.getMainHandItem().getDamageValue()==wear,"one mining action preserves normal tool wear");
            PhaseManager.exit(p);
            h.assertTrue(h.getLevel().getBlockState(bottom).isAir()&&h.getLevel().getBlockState(other).isAir(),"pair stays destroyed after exit");
            h.assertTrue(!FogRecovery.get(h.getLevel()).occupied(bottom)&&!FogRecovery.get(h.getLevel()).occupied(other),"exit clears fog ownership");
            h.assertTrue(drops(h,bottom,block.asItem())==1,"exit adds no duplicate drop");
            h.succeed();
        } finally {PhaseManager.exit(p);}
    }
    @GameTest(template="empty") public static void companionBreakCancellationLeavesWholePair(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK3);
        var bottom=p.blockPosition().east(2);var block=ModContent.TREATMENT_MACHINE.get();var top=pair(h,bottom,block);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
        PhaseManager.enter(p);var s=PhaseManager.get(p);
        Consumer<BlockEvent.BreakEvent> cancel=e->{if(e.getPlayer()==p&&e.getPos().equals(top))e.setCanceled(true);};
        NeoForge.EVENT_BUS.addListener(cancel);
        try {
            aimAt(s,bottom);
            h.assertTrue(!PhaseSkills.breakBlock(s,bottom),"companion protection cancels entire break");
            for(var part:List.of(bottom,top))h.assertTrue(h.getLevel().getBlockState(part)==s.snapshot().getBlockState(part)&&h.getLevel().getBlockState(part).is(block),"cancellation preserves both real and frozen parts");
            h.assertTrue(drops(h,bottom,block.asItem())==0&&p.getMainHandItem().getDamageValue()==0,"canceled break drops nothing and consumes no durability");
            h.succeed();
        } finally {NeoForge.EVENT_BUS.unregister(cancel);PhaseManager.exit(p);}
    }
    @GameTest(template="empty") public static void changedCompanionRejectsWholeBreak(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK3);
        var bottom=p.blockPosition().east(2);var top=pair(h,bottom,Blocks.OAK_DOOR);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_AXE));
        PhaseManager.enter(p);var s=PhaseManager.get(p);
        try {
            // Suppress neighbor shape updates to isolate an independently changed companion.
            h.getLevel().setBlock(top,Blocks.STONE.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
            aimAt(s,bottom);
            h.assertTrue(!PhaseSkills.breakBlock(s,bottom),"stale companion rejects whole break");
            h.assertTrue(h.getLevel().getBlockState(bottom).is(Blocks.OAK_DOOR)&&h.getLevel().getBlockState(top).is(Blocks.STONE),"neither real position overwritten");
            h.assertTrue(s.snapshot().getBlockState(bottom).is(Blocks.OAK_DOOR)&&s.snapshot().getBlockState(top).is(Blocks.OAK_DOOR),"rejected break preserves frozen pair");
            h.assertTrue(drops(h,bottom,Items.OAK_DOOR)==0&&p.getMainHandItem().getDamageValue()==0,"rejected break has no cost or drops");
            h.succeed();
        } finally {PhaseManager.exit(p);}
    }
    @GameTest(template="empty") public static void unrelatedNeighborRemovalStaysFrozen(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK3);
        var bottom=p.blockPosition().east(2);
        h.getLevel().setBlock(bottom,Blocks.STONE.defaultBlockState(),3);
        h.getLevel().setBlock(bottom.above(),Blocks.TORCH.defaultBlockState(),3);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
        PhaseManager.enter(p);var s=PhaseManager.get(p);
        try {
            SkillGameTests.aim(p,Vec3.atCenterOf(bottom));
            h.assertTrue(PhaseSkills.breakBlock(s,bottom),"support can be broken");
            h.assertTrue(h.getLevel().getBlockState(bottom.above()).isAir(),"fixture: live unsupported torch removed");
            h.assertTrue(s.snapshot().getBlockState(bottom.above()).is(Blocks.TORCH),"unrelated neighbor remains frozen");h.succeed();
        } finally {PhaseManager.exit(p);}
    }
    @GameTest(template="empty") public static void treatmentPlacementUpperCancellationRollsBackBoth(GameTestHelper h) {
        rejectedPlacement(h,"cancel");
    }
    @GameTest(template="empty") public static void treatmentPlacementChecksLiveUpperObstruction(GameTestHelper h) {
        rejectedPlacement(h,"blocked");
    }
    @GameTest(template="empty") public static void treatmentPlacementChecksUpperPlayerCollision(GameTestHelper h) {
        rejectedPlacement(h,"collision");
    }
    private static void rejectedPlacement(GameTestHelper h,String mode) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK2);
        var support=p.blockPosition().east(2).below();var bottom=support.above();var top=bottom.above();
        h.getLevel().setBlock(support,Blocks.STONE.defaultBlockState(),2);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModContent.TREATMENT_MACHINE.get(),2));
        PhaseManager.enter(p);var s=PhaseManager.get(p);
        Consumer<BlockEvent.EntityPlaceEvent> cancel=e->{if(mode.equals("cancel")&&e.getEntity()==p&&e.getPos().equals(top))e.setCanceled(true);};
        NeoForge.EVENT_BUS.addListener(cancel);
        try {
            if(mode.equals("blocked"))h.getLevel().setBlock(top,Blocks.STONE.defaultBlockState(),2);
            if(mode.equals("collision"))p.setPos(Vec3.atBottomCenterOf(top));
            var hit=Vec3.atCenterOf(support).add(0,.5,0);SkillGameTests.aim(p,hit);
            h.assertTrue(!PhaseSkills.place(s,support,Direction.UP,InteractionHand.MAIN_HAND,hit),"upper "+mode+" rejects entire chamber");
            h.assertTrue(s.snapshot().getBlockState(bottom).isAir()&&s.snapshot().getBlockState(top).isAir(),"no partial frozen chamber");
            h.assertTrue(h.getLevel().getBlockState(bottom).isAir()&&(mode.equals("blocked")?h.getLevel().getBlockState(top).is(Blocks.STONE):h.getLevel().getBlockState(top).isAir()),"rejection preserves real cells");
            h.assertTrue(s.placedBefore.isEmpty()&&!FogRecovery.get(h.getLevel()).occupied(bottom)&&!FogRecovery.get(h.getLevel()).occupied(top),"rejection creates no placement or fog ownership");
            h.assertTrue(p.getMainHandItem().getCount()==2,"rejection consumes no item");h.succeed();
        } finally {NeoForge.EVENT_BUS.unregister(cancel);PhaseManager.exit(p);}
    }
}
