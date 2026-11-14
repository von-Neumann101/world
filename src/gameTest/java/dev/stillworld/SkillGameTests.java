package dev.stillworld;
import dev.stillworld.core.Rank;
import dev.stillworld.server.PhaseManager;
import dev.stillworld.server.PhaseSkills;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Stillworld.ID)
@PrefixGameTestTemplate(false)
public final class SkillGameTests {
    static void aim(ServerPlayer p, Vec3 point) {
        Vec3 d = point.subtract(p.getEyePosition());
        p.setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z)));
        p.setXRot((float)-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x*d.x+d.z*d.z))));
    }
    @GameTest(template="empty") public static void blackMistIsTemporaryAndConsumesOneItem(GameTestHelper h) {
        var p=SessionGameTests.player(h); SessionGameTests.equip(p, Rank.RANK2);
        BlockPos support=p.blockPosition().east(2).below();
        h.getLevel().setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT,2));
        PhaseManager.enter(p); var s=PhaseManager.get(p);
        Vec3 hit=Vec3.atCenterOf(support).add(0,0.5,0); aim(p,hit);
        h.assertTrue(PhaseSkills.place(s,support,Direction.UP,InteractionHand.MAIN_HAND,hit),"rank2 placement should succeed");
        h.assertTrue(h.getLevel().getBlockState(support.above()).is(ModContent.BLACK_MIST.get()),"reality should contain black mist");
        h.assertTrue(s.snapshot().getBlockState(support.above()).is(Blocks.DIRT),"frozen view should contain the placed dirt");
        h.assertTrue(p.getMainHandItem().getCount()==1,"placement consumes exactly one item");
        PhaseManager.exit(p);
        h.assertTrue(h.getLevel().getBlockState(support.above()).isAir(),"black mist vanishes on exit"); h.succeed();
    }
    @GameTest(template="empty") public static void grayMistLeavesPermanentAir(GameTestHelper h) {
        var p=SessionGameTests.player(h); SessionGameTests.equip(p,Rank.RANK3);
        BlockPos block=p.blockPosition().east(2); h.getLevel().setBlockAndUpdate(block,Blocks.DIRT.defaultBlockState());
        PhaseManager.enter(p); aim(p,Vec3.atCenterOf(block));
        h.assertTrue(PhaseSkills.breakBlock(PhaseManager.get(p),block),"rank3 may break the captured dirt");
        h.assertTrue(h.getLevel().getBlockState(block).is(ModContent.GRAY_MIST.get()),"reality should contain gray mist");
        PhaseManager.exit(p); h.assertTrue(h.getLevel().getBlockState(block).isAir(),"rank3 destruction stays after exit"); h.succeed();
    }
    @GameTest(template="empty") public static void markingRewindsOnceAndDeferredDamageSettlesOnce(GameTestHelper h) {
        var p=SessionGameTests.player(h); SessionGameTests.equip(p,Rank.RANK5);
        var cow=h.spawnWithNoFreeWill(EntityType.COW,new BlockPos(5,2,3));
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);cow.setHealth(100);
        var entry=cow.position();
        p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10);p.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1024);
        PhaseManager.enter(p);var s=PhaseManager.get(p);
        cow.setPos(entry.add(5,0,0));cow.setHealth(7);aim(p,entry.add(0,cow.getBbHeight()/2,0));
        h.assertTrue(PhaseSkills.mark(s,cow.getUUID()),"a live captured cow can be marked");
        h.assertTrue(cow.position().distanceTo(entry)<0.001 && cow.getHealth()==100,"mark restores entry position and health");
        cow.setHealth(90);PhaseSkills.mark(s,cow.getUUID());
        h.assertTrue(cow.getHealth()==90,"a repeated mark cannot heal again");
        h.assertTrue(PhaseSkills.attack(s,cow.getUUID()) && PhaseSkills.attack(s,cow.getUUID()),"marked target accepts two valid attacks");
        h.assertTrue(cow.getHealth()==90,"rank5 does not damage immediately");
        PhaseManager.exit(p);h.assertTrue(Math.abs(cow.getHealth()-58)<0.01,"exit applies 1.6 times twenty base damage");
        PhaseManager.exit(p);h.assertTrue(Math.abs(cow.getHealth()-58)<0.01,"second exit cannot repeat the payout");h.succeed();
    }
    @GameTest(template="empty") public static void deadRealityCannotBeRecreatedByMark(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK4);
        var cow=h.spawnWithNoFreeWill(EntityType.COW,new BlockPos(5,2,3));PhaseManager.enter(p);
        cow.hurt(cow.damageSources().genericKill(),1000);
        h.assertTrue(!PhaseSkills.mark(PhaseManager.get(p),cow.getUUID()),"dead target must not be revived");
        h.assertTrue(!cow.isAlive(),"no resurrection");PhaseManager.exit(p);h.succeed();
    }
}
