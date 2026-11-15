package dev.stillworld;

import dev.stillworld.core.Rank;
import dev.stillworld.server.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Stillworld.ID)
@PrefixGameTestTemplate(false)
public final class ReviewRegressionGameTests {
    @GameTest(template="empty") public static void doorRemovalMustClearBothHalves(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK2);
        var support=p.blockPosition().east(2).below();
        h.getLevel().setBlockAndUpdate(support,Blocks.STONE.defaultBlockState());
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.OAK_DOOR,2));
        PhaseManager.enter(p);var s=PhaseManager.get(p);
        try {
            var hit=Vec3.atCenterOf(support).add(0,.5,0);SkillGameTests.aim(p,hit);
            h.assertTrue(PhaseSkills.place(s,support,Direction.UP,InteractionHand.MAIN_HAND,hit),"fixture: door placement accepted");
            var bottom=support.above();var top=bottom.above();
            h.assertTrue(s.snapshot().getBlockState(top).is(Blocks.OAK_DOOR),"fixture: upper door captured");
            SkillGameTests.aim(p,Vec3.atCenterOf(bottom));
            h.assertTrue(PhaseSkills.breakBlock(s,bottom),"fixture: lower door removed");
            h.assertTrue(s.snapshot().getBlockState(top).isAir()&&!h.getLevel().getBlockState(top).is(ModContent.BLACK_MIST.get()),"BUG: removing placed door leaves upper snapshot door and black mist");
            h.succeed();
        } finally { PhaseManager.exit(p); }
    }
    @GameTest(template="empty") public static void wallTorchMustUseWallVariant(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK2);
        var wall=p.blockPosition().east(2).above();
        h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(wall.west().below(),Blocks.AIR.defaultBlockState());
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.TORCH,2));
        PhaseManager.enter(p);var s=PhaseManager.get(p);
        try {
            var hit=Vec3.atCenterOf(wall).add(-.5,0,0);SkillGameTests.aim(p,hit);
            h.assertTrue(PhaseSkills.place(s,wall,Direction.WEST,InteractionHand.MAIN_HAND,hit),"BUG: valid wall torch placement is rejected");
            h.assertTrue(s.snapshot().getBlockState(wall.west()).is(Blocks.WALL_TORCH),"wall variant must be used");h.succeed();
        } finally {PhaseManager.exit(p);}
    }
    @GameTest(template="empty") public static void safeExitMustNotChooseLava(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK1);
        var pos=p.blockPosition();PhaseManager.enter(p);
        // Every first-radius free cell is lava; clear supported space exists two blocks east.
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<2;y++)
            h.getLevel().setBlock(pos.offset(x,y,z),Blocks.LAVA.defaultBlockState(),2);
        h.getLevel().setBlock(pos,Blocks.STONE.defaultBlockState(),2);
        h.getLevel().setBlock(pos.above(),Blocks.STONE.defaultBlockState(),2);
        var safe=pos.east(2);h.getLevel().setBlock(safe.below(),Blocks.STONE.defaultBlockState(),2);
        h.getLevel().setBlock(safe,Blocks.AIR.defaultBlockState(),2);h.getLevel().setBlock(safe.above(),Blocks.AIR.defaultBlockState(),2);
        PhaseManager.exit(p);
        h.assertTrue(h.getLevel().getFluidState(p.blockPosition()).isEmpty(),"BUG: automatic safe exit teleports into lava at "+p.blockPosition());h.succeed();
    }
    @GameTest(template="empty") public static void teleportAndEnterMustNotSeedDistantOldChunks(GameTestHelper h) {
        var received=new java.util.ArrayList<net.minecraft.network.protocol.Packet<?>>();
        var p=SessionGameTests.player(h,received::add);SessionGameTests.equip(p,Rank.INFINITY);
        var oldCenter=p.chunkPosition();
        p.setChunkTrackingView(net.minecraft.server.level.ChunkTrackingView.of(oldCenter,2));
        // Same-level vanilla teleport changes position immediately. Tracking updates on the next chunk-map tick.
        p.teleportTo(p.getX()+1_000_000,p.getY(),p.getZ());
        h.assertTrue(p.getChunkTrackingView().contains(oldCenter.x,oldCenter.z),"fixture: vanilla teleport preserves old tracking until next update");
        PhaseManager.enter(p);
        try {
            int count=0,maxDistance=0;
            for(var packet:received) if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                &&custom.payload() instanceof dev.stillworld.network.RealityPayload reality
                &&reality.packet() instanceof net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket chunk) {
                count++;maxDistance=Math.max(maxDistance,Math.max(Math.abs(chunk.getX()-p.chunkPosition().x),Math.abs(chunk.getZ()-p.chunkPosition().z)));
            }
            h.assertTrue(count>0,"fixture: received real mirror seed chunks");
            System.out.println("REVIEW distant seed: packets="+count+", radius="+maxDistance+", vanillaStorageLength="+((2*(maxDistance+3)+1)*(2*(maxDistance+3)+1)));
            h.assertTrue(maxDistance<128,"BUG: real seed includes old chunks "+maxDistance+" chunks away; client uses this as square cache radius");h.succeed();
        } finally {PhaseManager.exit(p);}
    }
    @GameTest(template="empty") public static void frozenLadderMustSuppressCriticalDamage(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK5);var pos=p.blockPosition();
        h.getLevel().setBlock(pos.east(),Blocks.STONE.defaultBlockState(),2);
        h.getLevel().setBlock(pos,Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.WEST),2);
        var cow=h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW,new BlockPos(3,2,5));
        p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(10);
        p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED).setBaseValue(1024);
        PhaseManager.enter(p);var s=PhaseManager.get(p);
        try {
            h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),2);
            h.assertTrue(s.snapshot().getBlockState(pos).is(Blocks.LADDER),"fixture: ladder retained in snapshot");
            SkillGameTests.aim(p,cow.getBoundingBox().getCenter());
            h.assertTrue(PhaseSkills.mark(s,cow.getUUID()),"fixture: nearby cow marked");
            p.setOnGround(false);p.fallDistance=1;
            h.assertTrue(PhaseSkills.attack(s,cow.getUUID()),"fixture: attack accepted");
            float recorded=s.damage.accumulated(cow.getUUID());
            h.assertTrue(Math.abs(recorded-10)<.01,"BUG: player on frozen ladder gets critical damage "+recorded+" instead of 10 after live ladder removal");h.succeed();
        } finally {PhaseManager.exit(p);cow.discard();}
    }
    @GameTest(template="empty") public static void safeExitMustRecheckBlockedEntry(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK1);var pos=p.blockPosition();
        PhaseManager.enter(p);
        for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++)for(int y=0;y<=10;y++)
            h.getLevel().setBlock(pos.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
        PhaseManager.exit(p);
        h.assertTrue(h.getLevel().noCollision(p,p.getBoundingBox()),"BUG: fallback teleports into now-obstructed entry");h.succeed();
    }
}
