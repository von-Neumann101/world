package dev.stillworld;

import dev.stillworld.core.Rank;
import dev.stillworld.network.PhaseNetwork;
import dev.stillworld.server.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder(Stillworld.ID)
@PrefixGameTestTemplate(false)
public final class SettlementGameTests {
    @GameTest(template="empty") public static void maceRecordsSmashWithoutMovingReality(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK5);
        var mace=new ItemStack(Items.MACE);p.setItemInHand(InteractionHand.MAIN_HAND,mace);
        p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10);p.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1024);
        var cow=h.spawnWithNoFreeWill(EntityType.COW,new BlockPos(5,2,3));
        var bystander=h.spawnWithNoFreeWill(EntityType.COW,new BlockPos(5,2,5));
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);cow.setHealth(100);
        PhaseManager.enter(p);var s=PhaseManager.get(p);SkillGameTests.aim(p,cow.getBoundingBox().getCenter());
        h.assertTrue(PhaseSkills.mark(s,cow.getUUID()),"target marked");
        p.fallDistance=4;p.setSprinting(true);var motion=p.getDeltaMovement();var nearbyMotion=bystander.getDeltaMovement();
        h.assertTrue(PhaseSkills.attack(s,cow.getUUID()),"falling mace attack is accepted without immediate hit effects");
        h.assertTrue(Math.abs(s.damage.accumulated(cow.getUUID())-24)<.01,"mace adds fourteen smash damage to ten base damage");
        h.assertTrue(p.getDeltaMovement().equals(motion)&&bystander.getDeltaMovement().equals(nearbyMotion),"recording does not launch the player or nearby unmarked mobs");
        h.assertTrue(mace.getDamageValue()==1,"accepted melee attack consumes ordinary durability");
        h.assertTrue(cow.getHealth()==100,"smash damage remains deferred");
        PhaseManager.exit(p);h.assertTrue(Math.abs(cow.getHealth()-61.6)<.01,"smash settles once at 1.6 multiplier");h.succeed();
    }
    @GameTest(template="empty") public static void phasedPlayerCannotCollectExperience(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK1);
        var orb=new net.minecraft.world.entity.ExperienceOrb(h.getLevel(),p.getX(),p.getY(),p.getZ(),5);
        h.getLevel().addFreshEntity(orb);PhaseManager.enter(p);
        p.aiStep();
        h.assertTrue(!orb.isRemoved()&&p.totalExperience==0,"walking over XP does not collect or mend while phased");
        PhaseManager.exit(p);p.aiStep();
        h.assertTrue(orb.isRemoved()&&p.totalExperience==5,"normal pickup resumes on exit");h.succeed();
    }
    @GameTest(template="empty") public static void slashUsesExitPositionsAndOnlySettlesOnce(GameTestHelper h) {
        var p=SessionGameTests.player(h); SessionGameTests.equip(p,Rank.RANK6);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModContent.BLADE.get()));
        p.setYRot(0); p.setXRot(0);
        var incoming=h.spawnWithNoFreeWill(EntityType.COW,new BlockPos(9,2,3));
        var outgoing=h.spawnWithNoFreeWill(EntityType.COW,new BlockPos(9,2,5));
        for(var cow:java.util.List.of(incoming,outgoing)) {cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);cow.setHealth(100);}
        var center=p.getEyePosition().add(0,0,2);
        outgoing.setPos(center.add(0,-0.5,0));
        PhaseManager.enter(p);var s=PhaseManager.get(p);
        var action=PhaseNetwork.message("slash");action.putUUID("session",s.id);PhaseManager.handle(p,action);
        h.assertTrue(s.slashes.size()==1,"blade creates one stationary slash");
        h.assertTrue(incoming.getHealth()==100&&outgoing.getHealth()==100,"creating slash causes no immediate damage");
        outgoing.setPos(center.add(7,0,0)); incoming.setPos(center.add(0,-0.5,0));
        PhaseManager.exit(p);
        h.assertTrue(Math.abs(incoming.getHealth()-70)<0.01,"unmarked mob moving into slash receives thirty percent maximum health");
        h.assertTrue(outgoing.getHealth()==100,"mob moving out is not hit at its old position");
        PhaseManager.exit(p); h.assertTrue(incoming.getHealth()==70,"repeat exit cannot settle the slash again"); h.succeed();
    }
    @GameTest(template="empty") public static void lowerRanksCannotMutateOrMark(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK1);
        var block=p.blockPosition().east(2).below();h.getLevel().setBlockAndUpdate(block,Blocks.STONE.defaultBlockState());
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIRT,2));
        var cow=h.spawnWithNoFreeWill(EntityType.COW,new BlockPos(5,2,3));
        PhaseManager.enter(p);var s=PhaseManager.get(p);var hit=Vec3.atCenterOf(block).add(0,.5,0);SkillGameTests.aim(p,hit);
        h.assertTrue(!PhaseSkills.place(s,block,Direction.UP,InteractionHand.MAIN_HAND,hit),"rank1 cannot place");
        h.assertTrue(!PhaseSkills.breakBlock(s,block),"rank1 cannot break");
        SkillGameTests.aim(p,cow.getBoundingBox().getCenter());
        h.assertTrue(!PhaseSkills.mark(s,cow.getUUID()),"rank1 cannot mark");
        h.assertTrue(!PhaseSkills.attack(s,cow.getUUID()),"rank1 cannot attack");
        h.assertTrue(p.getMainHandItem().getCount()==2,"rejected placement consumes nothing");
        PhaseManager.exit(p);SessionGameTests.equip(p,Rank.RANK2);PhaseManager.enter(p);SkillGameTests.aim(p,hit);
        h.assertTrue(!PhaseSkills.breakBlock(PhaseManager.get(p),block),"rank2 cannot break original blocks");
        h.assertTrue(h.getLevel().getBlockState(block).is(Blocks.STONE),"rejected actions leave reality intact");
        PhaseManager.exit(p);h.succeed();
    }
    @GameTest(template="empty") public static void marksAreExclusiveAndReleasedOnExit(GameTestHelper h) {
        var first=SessionGameTests.player(h);var second=SessionGameTests.player(h);
        SessionGameTests.equip(first,Rank.RANK4);SessionGameTests.equip(second,Rank.RANK4);
        var cow=h.spawnWithNoFreeWill(EntityType.COW,new BlockPos(5,2,3));
        PhaseManager.enter(first);PhaseManager.enter(second);
        SkillGameTests.aim(first,cow.getBoundingBox().getCenter());SkillGameTests.aim(second,cow.getBoundingBox().getCenter());
        h.assertTrue(PhaseSkills.mark(PhaseManager.get(first),cow.getUUID()),"first player acquires mark");
        h.assertTrue(!PhaseSkills.mark(PhaseManager.get(second),cow.getUUID()),"second player cannot steal a mark");
        PhaseManager.exit(first);
        h.assertTrue(PhaseSkills.mark(PhaseManager.get(second),cow.getUUID()),"exit releases ownership");
        PhaseManager.exit(second);h.succeed();
    }
    @GameTest(template="empty") public static void orphanMistCleanupPreservesExternalReplacements(GameTestHelper h) {
        var level=h.getLevel();var recovery=FogRecovery.get(level);var owner=UUID.randomUUID();
        var black=h.absolutePos(new BlockPos(2,2,2));var gray=black.east();var replaced=gray.east();
        level.setBlockAndUpdate(black,ModContent.BLACK_MIST.get().defaultBlockState());
        level.setBlockAndUpdate(gray,ModContent.GRAY_MIST.get().defaultBlockState());
        level.setBlockAndUpdate(replaced,ModContent.BLACK_MIST.get().defaultBlockState());
        recovery.track(black,owner,false);recovery.track(gray,owner,true);recovery.track(replaced,owner,false);
        level.setBlockAndUpdate(replaced,Blocks.DIAMOND_BLOCK.defaultBlockState());
        for(var pos:java.util.List.of(black,gray,replaced))recovery.recoverChunk(level,new ChunkPos(pos));
        h.assertTrue(level.getBlockState(black).isAir()&&level.getBlockState(gray).isAir(),"inactive session fog is cleaned on chunk recovery");
        h.assertTrue(level.getBlockState(replaced).is(Blocks.DIAMOND_BLOCK),"cleanup cannot erase a real replacement");
        h.assertTrue(!recovery.occupied(replaced),"replacement position releases fog ownership");h.succeed();
    }
}
