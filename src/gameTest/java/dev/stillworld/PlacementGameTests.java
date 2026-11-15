package dev.stillworld;

import dev.stillworld.core.Rank;
import dev.stillworld.server.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Stillworld.ID)
@PrefixGameTestTemplate(false)
public final class PlacementGameTests {
    @GameTest(template="empty") public static void snapshotPlacementStillRejectsPlayerOverlap(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK2);
        var support=p.blockPosition().below();
        h.getLevel().setBlock(support,Blocks.STONE.defaultBlockState(),2);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIRT,2));
        PhaseManager.enter(p);var s=PhaseManager.get(p);
        try {
            var hit=Vec3.atCenterOf(support).add(0,.5,0);SkillGameTests.aim(p,hit);
            h.assertTrue(!PhaseSkills.place(s,support,Direction.UP,InteractionHand.MAIN_HAND,hit),"snapshot placement cannot trap its own player");
            h.assertTrue(s.snapshot().getBlockState(support.above()).isAir()&&h.getLevel().getBlockState(support.above()).isAir(),"rejected overlap writes neither snapshot nor reality");
            h.assertTrue(p.getMainHandItem().getCount()==2,"rejected overlap consumes no item");
            h.succeed();
        } finally {PhaseManager.exit(p);}
    }
    @GameTest(template="empty") public static void laterRealityMobDoesNotBlockSnapshotPlacement(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK2);
        var support=p.blockPosition().east(2).below();var target=support.above();
        h.getLevel().setBlock(support,Blocks.STONE.defaultBlockState(),2);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIRT,2));
        PhaseManager.enter(p);var s=PhaseManager.get(p);
        var cow=h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW,h.relativePos(target));
        cow.setPos(Vec3.atBottomCenterOf(target));
        try {
            var dirt=Blocks.DIRT.defaultBlockState();
            h.assertTrue(!h.getLevel().isUnobstructed(dirt,target,net.minecraft.world.phys.shapes.CollisionContext.empty()),"fixture: live cow blocks ordinary placement");
            var hit=Vec3.atCenterOf(support).add(0,.5,0);SkillGameTests.aim(p,hit);
            h.assertTrue(PhaseSkills.place(s,support,Direction.UP,InteractionHand.MAIN_HAND,hit),"later reality entity must not block placement in frozen view");
            h.assertTrue(s.snapshot().getBlockState(target).is(Blocks.DIRT)&&h.getLevel().getBlockState(target).is(ModContent.BLACK_MIST.get()),"placement retains snapshot and owned reality mist");
            h.assertTrue(!h.getLevel().isUnobstructed(dirt,target,net.minecraft.world.phys.shapes.CollisionContext.empty()),"normal reality obstruction restored after scoped query");
            h.succeed();
        } finally {PhaseManager.exit(p);cow.discard();}
    }
    @GameTest(template="empty") public static void removingDoorTopRestoresWholePlacement(GameTestHelper h) {
        removePart(h,Items.OAK_DOOR,true,false);
    }
    @GameTest(template="empty") public static void removingPlantTopRestoresReplacedGrass(GameTestHelper h) {
        removePart(h,Items.SUNFLOWER,true,true);
    }
    @GameTest(template="empty") public static void removingBedHeadRestoresWholePlacement(GameTestHelper h) {
        removePart(h,Items.RED_BED,true,false);
    }
    @GameTest(template="empty") public static void removingBedFootRestoresWholePlacement(GameTestHelper h) {
        removePart(h,Items.RED_BED,false,false);
    }
    @GameTest(template="empty") public static void removingTreatmentTopRestoresWholePlacement(GameTestHelper h) {
        removePart(h,ModContent.TREATMENT_MACHINE.get().asItem(),true,false);
    }
    @GameTest(template="empty") public static void removingTreatmentBottomRestoresWholePlacement(GameTestHelper h) {
        removePart(h,ModContent.TREATMENT_MACHINE.get().asItem(),false,false);
    }
    private static void removePart(GameTestHelper h,Item item,boolean second,boolean grass) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK2);
        var support=p.blockPosition().east(2).below();var bottom=support.above();
        h.getLevel().setBlock(support,Blocks.DIRT.defaultBlockState(),2);
        h.getLevel().setBlock(support.east(),Blocks.DIRT.defaultBlockState(),2);
        var before=grass?Blocks.SHORT_GRASS.defaultBlockState():Blocks.AIR.defaultBlockState();
        h.getLevel().setBlock(bottom,before,2);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item,2));
        PhaseManager.enter(p);var s=PhaseManager.get(p);
        try {
            var hit=Vec3.atCenterOf(support).add(0,.5,0);SkillGameTests.aim(p,hit);
            // A replaceable plant is itself the outline hit when it covers the support.
            var clicked=grass?bottom:support;
            if(grass){hit=Vec3.atBottomCenterOf(bottom).add(0,.2,0);SkillGameTests.aim(p,hit);}
            h.assertTrue(PhaseSkills.place(s,clicked,Direction.UP,InteractionHand.MAIN_HAND,hit),"multi-block placement accepted");
            var state=s.snapshot().getBlockState(bottom);
            var other=item==Items.RED_BED?bottom.relative(state.getValue(BedBlock.FACING)):bottom.above();
            h.assertTrue(s.placedBefore.size()==2,"both parts recorded");
            h.assertTrue(s.snapshot().getBlockState(other).is(state.getBlock()),"both parts have the same block type");
            h.assertTrue(h.getLevel().getBlockState(bottom).is(ModContent.BLACK_MIST.get())&&h.getLevel().getBlockState(other).is(ModContent.BLACK_MIST.get()),"both parts have reality mist");
            h.assertTrue(p.getMainHandItem().getCount()==1,"one item consumed for both parts");
            var removed=second?other:bottom;
            var target=s.snapshot().getBlockState(removed).getShape(s.snapshot(),removed).bounds().getCenter().add(Vec3.atLowerCornerOf(removed));
            p.setPos(second||item==Items.RED_BED?target.add(0,2,0):target.add(-2,-.6,0));
            SkillGameTests.aim(p,target);
            h.assertTrue(PhaseSkills.breakBlock(s,removed),"either member can remove a rank2 placement");
            h.assertTrue(s.snapshot().getBlockState(bottom)==before&&s.snapshot().getBlockState(other).isAir(),"all snapshot states restored");
            h.assertTrue(!h.getLevel().getBlockState(bottom).is(ModContent.BLACK_MIST.get())&&!h.getLevel().getBlockState(other).is(ModContent.BLACK_MIST.get()),"all owned mist removed");
            h.assertTrue(!FogRecovery.get(h.getLevel()).occupied(bottom)&&!FogRecovery.get(h.getLevel()).occupied(other)&&s.placedBefore.isEmpty(),"all placement ownership released");
            h.succeed();
        } finally {PhaseManager.exit(p);}
    }
}
