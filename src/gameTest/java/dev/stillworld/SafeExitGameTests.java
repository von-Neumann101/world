package dev.stillworld;

import dev.stillworld.core.Rank;
import dev.stillworld.server.PhaseManager;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Stillworld.ID)
@PrefixGameTestTemplate(false)
public final class SafeExitGameTests {
    @GameTest(template="empty") public static void relocationRequiresSupport(GameTestHelper h) {
        var player=SessionGameTests.player(h);SessionGameTests.equip(player,Rank.RANK1);
        player.setPos(player.position().add(0,12,0));
        var pos=player.blockPosition();PhaseManager.enter(player);
        h.getLevel().setBlock(pos,Blocks.STONE.defaultBlockState(),2);
        h.getLevel().setBlock(pos.above(),Blocks.STONE.defaultBlockState(),2);
        h.getLevel().setBlock(pos.east(2).below(),Blocks.STONE.defaultBlockState(),2);
        PhaseManager.exit(player);
        h.assertTrue(h.getLevel().noCollision(player,player.getBoundingBox()),"Relocation must clear the real obstruction");
        h.assertTrue(!h.getLevel().noCollision(player,player.getBoundingBox().move(0,-.05,0)),"Automatic relocation must have a supporting floor instead of an unsupported fall");
        h.succeed();
    }

    @GameTest(template="empty") public static void relocationChecksHeadLevelFluid(GameTestHelper h) {
        hazardRelocation(h,true);
    }

    @GameTest(template="empty") public static void relocationChecksDangerousFloor(GameTestHelper h) {
        hazardRelocation(h,false);
    }

    private static void hazardRelocation(GameTestHelper h,boolean headFluid) {
        var player=SessionGameTests.player(h);SessionGameTests.equip(player,Rank.RANK1);
        var pos=player.blockPosition();PhaseManager.enter(player);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
            var nearby=pos.offset(x,0,z);
            h.getLevel().setBlock(nearby.below(),(headFluid?Blocks.STONE:Blocks.MAGMA_BLOCK).defaultBlockState(),2);
            h.getLevel().setBlock(nearby,Blocks.AIR.defaultBlockState(),2);
            h.getLevel().setBlock(nearby.above(),(headFluid?Blocks.LAVA:Blocks.AIR).defaultBlockState(),2);
        }
        h.getLevel().setBlock(pos,Blocks.STONE.defaultBlockState(),2);
        h.getLevel().setBlock(pos.above(),Blocks.STONE.defaultBlockState(),2);
        h.getLevel().setBlock(pos.east(2).below(),Blocks.STONE.defaultBlockState(),2);
        PhaseManager.exit(player);
        h.assertTrue(h.getLevel().getFluidState(player.blockPosition().above()).isEmpty(),"Head-level lava must reject an otherwise clear candidate");
        h.assertTrue(!h.getLevel().getBlockState(player.blockPosition().below()).is(Blocks.MAGMA_BLOCK),"Automatic relocation must not stand on a damaging floor");
        h.succeed();
    }

    @GameTest(template="empty") public static void absentSafeCandidateStillCleansSessionWithoutUnsafeTeleport(GameTestHelper h) {
        int[] teleports={0};
        var player=SessionGameTests.player(h,packet->{if(packet instanceof ClientboundPlayerPositionPacket)teleports[0]++;});
        SessionGameTests.equip(player,Rank.RANK1);
        player.setPos(new Vec3(player.getX(),h.getLevel().getMaxBuildHeight()-2,player.getZ()));
        var position=player.position();var pos=player.blockPosition();PhaseManager.enter(player);
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)for(int y=0;y<2;y++)
            h.getLevel().setBlock(pos.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
        PhaseManager.exit(player);
        h.assertTrue(PhaseManager.get(player)==null,"A failed relocation search must not retain a phase session");
        h.assertTrue(player.position().equals(position)&&teleports[0]==0,"No safe location means no unvalidated teleport or placement above the build limit");
        h.succeed();
    }
}
