package dev.stillworld;

import dev.stillworld.core.Rank;
import dev.stillworld.server.ForesightPrediction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

@GameTestHolder(Stillworld.ID)
@PrefixGameTestTemplate(false)
public final class ForesightGameTests {
    @GameTest(template="empty") public static void packetMotionSurvivesOldPositionReset(GameTestHelper h) {
        var player=SessionGameTests.player(h);var start=player.position();
        for(var pos:BlockPos.betweenClosed(player.blockPosition().offset(-1,0,-1),player.blockPosition().offset(5,3,1)))h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        var tracker=new ForesightPrediction.Tracker();
        h.assertTrue(tracker.predict(player,10).equals(start),"New track starts without invented motion");
        player.setPos(start.add(.2,0,0));player.setOldPosAndRot();
        h.assertTrue(tracker.predict(player,12).distanceToSqr(player.position().add(3,0,0))<1.0E-6,"Player movement sampled between ticks survives vanilla xOld reset");
        h.assertTrue(tracker.predict(player,14).equals(player.position()),"Stopping updates prediction");
        tracker.retainTick(16);
        player.setPos(start.add(1,0,0));
        h.assertTrue(tracker.predict(player,18).equals(player.position()),"Reacquired track does not reuse stale velocity");
        tracker.clear();h.succeed();
    }
    @GameTest(template="empty") public static void foresightHasIndependentFunctionalSlot(GameTestHelper h) {
        var item=BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Stillworld.ID,"foresight_lens"));
        h.assertTrue(item!=Items.AIR,"Foresight lens must be registered");
        var player=SessionGameTests.player(h);
        SessionGameTests.equip(player,Rank.RANK4_5);
        var slots=CuriosApi.getCuriosInventory(player).orElseThrow();
        var lensSlot=slots.getStacksHandler("foresight").orElseThrow();
        player.getInventory().add(new ItemStack(item));
        h.assertTrue(!ModContent.hasForesight(player),"Carried lens is inactive");
        lensSlot.getCosmeticStacks().setStackInSlot(0,new ItemStack(item));
        h.assertTrue(!ModContent.hasForesight(player),"Cosmetic lens is inactive");
        lensSlot.getStacks().setStackInSlot(0,new ItemStack(item));
        h.assertTrue(ModContent.hasForesight(player),"Functional lens is active");
        h.assertTrue(ModContent.equippedRank(player)==Rank.RANK4_5,"Lens must coexist with rank charm");
        lensSlot.getStacks().setStackInSlot(0,ItemStack.EMPTY);
        h.assertTrue(!ModContent.hasForesight(player),"Unequipping lens disables foresight");
        h.succeed();
    }

    @GameTest(template="empty") public static void predictionUsesThirtyTicksAndStopsAtWalls(GameTestHelper h) {
        var pig=h.spawn(EntityType.PIG,new BlockPos(2,2,2));pig.setNoAi(true);pig.setNoGravity(true);
        for(var pos:BlockPos.betweenClosed(h.absolutePos(new BlockPos(1,2,1)),h.absolutePos(new BlockPos(7,4,7))))h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        var start=pig.position();pig.xOld=start.x-.1;pig.yOld=start.y;pig.zOld=start.z;
        var velocity=pig.getDeltaMovement();var health=pig.getHealth();
        h.assertTrue(ForesightPrediction.predict(pig,pig.position().subtract(pig.xOld,pig.yOld,pig.zOld)).distanceToSqr(start.add(3,0,0))<1.0E-6,"1.5 seconds means 30 ticks");
        var wall=pig.blockPosition().east(2);h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());h.getLevel().setBlockAndUpdate(wall.above(),Blocks.STONE.defaultBlockState());
        var blocked=ForesightPrediction.predict(pig,pig.position().subtract(pig.xOld,pig.yOld,pig.zOld));
        h.assertTrue(Math.abs(blocked.x-(wall.getX()-pig.getBbWidth()/2.0))<1.0E-5,"Full body stops at wall instead of crossing it: start="+start+", result="+blocked+", wall="+wall+", box="+pig.getBoundingBox());
        h.assertTrue(pig.position().equals(start)&&pig.getDeltaMovement().equals(velocity)&&pig.getHealth()==health,"Prediction never changes real position, velocity or health");
        pig.xOld=start.x;
        h.assertTrue(ForesightPrediction.predict(pig,pig.position().subtract(pig.xOld,pig.yOld,pig.zOld)).equals(start),"Stationary entity stays stationary");
        pig.xOld=start.x-10;
        h.assertTrue(ForesightPrediction.predict(pig,pig.position().subtract(pig.xOld,pig.yOld,pig.zOld)).equals(start),"Teleport is not extrapolated");
        h.succeed();
    }

    @GameTest(template="empty") public static void predictionFollowsTurnsAndStopsAtRouteEnd(GameTestHelper h) {
        for(var pos:BlockPos.betweenClosed(h.absolutePos(new BlockPos(1,2,1)),h.absolutePos(new BlockPos(7,4,7))))h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        var pig=h.spawn(EntityType.PIG,new BlockPos(2,2,2));pig.setNoAi(true);pig.setNoGravity(true);
        var a=pig.blockPosition();pig.setPos(Vec3.atBottomCenterOf(a));var start=pig.position();
        var b=a.south(2);var c=b.east(3);
        var path=new Path(List.of(new Node(a.getX(),a.getY(),a.getZ()),new Node(b.getX(),b.getY(),b.getZ()),new Node(c.getX(),c.getY(),c.getZ())),c,true);
        pig.getNavigation().moveTo(path,1);path.setNextNodeIndex(1);
        pig.xOld=start.x;pig.yOld=start.y;pig.zOld=start.z-.1;
        h.assertTrue(ForesightPrediction.predict(pig,pig.position().subtract(pig.xOld,pig.yOld,pig.zOld)).distanceToSqr(start.add(1,0,2))<1.0E-6,"Prediction follows the right-angle turn, not the original straight velocity");
        h.assertTrue(path.getNextNodeIndex()==1&&pig.position().equals(start),"Reading route must not advance AI path or position");
        pig.zOld=start.z-.3;
        h.assertTrue(ForesightPrediction.predict(pig,pig.position().subtract(pig.xOld,pig.yOld,pig.zOld)).distanceToSqr(Vec3.atBottomCenterOf(c))<1.0E-6,"Prediction stops at final path node");
        h.getLevel().setBlockAndUpdate(b,Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(b.above(),Blocks.STONE.defaultBlockState());
        var obstructed=ForesightPrediction.predict(pig,pig.position().subtract(pig.xOld,pig.yOld,pig.zOld));
        h.assertTrue(obstructed.z<b.getZ()&&Math.abs(obstructed.x-start.x)<1.0E-6,"New obstacle invalidates the route projection before the turn");
        h.succeed();
    }
}
