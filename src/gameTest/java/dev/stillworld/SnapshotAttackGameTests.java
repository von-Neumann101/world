package dev.stillworld;

import dev.stillworld.core.Rank;
import dev.stillworld.server.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Stillworld.ID)
@PrefixGameTestTemplate(false)
public final class SnapshotAttackGameTests {
    @GameTest(template="empty") public static void realLadderMustNotSuppressSnapshotCritical(GameTestHelper h) {
        attack(h,Blocks.AIR.defaultBlockState(),Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.WEST),15,false);
    }
    @GameTest(template="empty") public static void frozenWaterMustSuppressCriticalAfterRealityDrains(GameTestHelper h) {
        attack(h,Blocks.WATER.defaultBlockState(),Blocks.AIR.defaultBlockState(),10,false);
    }
    @GameTest(template="empty") public static void realWaterMustNotSuppressDrySnapshotCritical(GameTestHelper h) {
        attack(h,Blocks.AIR.defaultBlockState(),Blocks.WATER.defaultBlockState(),15,true);
    }
    private static void attack(GameTestHelper h,BlockState frozen,BlockState live,float expected,boolean liveWater) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK5);var pos=p.blockPosition();
        h.getLevel().setBlock(pos.east(),Blocks.STONE.defaultBlockState(),2);
        h.getLevel().setBlock(pos,frozen,2);
        var cow=h.spawnWithNoFreeWill(EntityType.COW,new BlockPos(3,2,5));
        p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10);
        p.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1024);
        PhaseManager.enter(p);var s=PhaseManager.get(p);
        try {
            h.getLevel().setBlock(pos,live,2);
            p.baseTick(); // Populate vanilla fluid and block caches from the live level.
            h.assertTrue(p.isInWater()==liveWater,"fixture: vanilla water cache reflects reality");
            h.assertTrue(p.getInBlockState()==live,"fixture: vanilla block cache reflects reality");
            SkillGameTests.aim(p,cow.getBoundingBox().getCenter());
            h.assertTrue(PhaseSkills.mark(s,cow.getUUID()),"nearby target marked");
            p.setOnGround(false);p.fallDistance=1;
            h.assertTrue(PhaseSkills.attack(s,cow.getUUID()),"falling charged attack accepted");
            h.assertTrue(Math.abs(s.damage.accumulated(cow.getUUID())-expected)<.01,"critical damage must use frozen environment, expected "+expected+" got "+s.damage.accumulated(cow.getUUID()));
            h.assertTrue(p.isInWater()==liveWater&&p.getInBlockState()==live,"attack must not replace live entity caches");
            h.succeed();
        } finally {PhaseManager.exit(p);cow.discard();}
    }
}
