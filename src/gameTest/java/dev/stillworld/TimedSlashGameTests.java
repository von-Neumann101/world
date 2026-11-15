package dev.stillworld;

import dev.stillworld.core.Rank;
import dev.stillworld.network.PhaseNetwork;
import dev.stillworld.server.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Stillworld.ID)
@PrefixGameTestTemplate(false)
public final class TimedSlashGameTests {
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.INFINITY);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModContent.BLADE.get()));p.setYRot(0);p.setXRot(0);return p;
    }
    private static net.minecraft.world.entity.animal.Cow cow(GameTestHelper h,Vec3 pos) {
        var cow=h.spawnWithNoFreeWill(EntityType.COW,new BlockPos(8,2,3));cow.setNoGravity(true);cow.setPos(pos);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);cow.setHealth(100);return cow;
    }
    private static void slash(net.minecraft.server.level.ServerPlayer p) {
        var tag=PhaseNetwork.message("slash");tag.putUUID("session",PhaseManager.get(p).id);PhaseManager.handle(p,tag);
    }
    @GameTest(template="empty",timeoutTicks=80) public static void delayedSlashUsesLivePositionAndOnlyHitsOnce(GameTestHelper h) {
        var p=player(h);var center=p.getEyePosition().add(0,0,2);
        var ghostOnly=cow(h,center.add(0,-.5,0));PhaseManager.enter(p);var s=PhaseManager.get(p);slash(p);
        var incoming=cow(h,center.add(6,-.5,0));var miss=cow(h,center.add(0,-.5,1));
        h.assertTrue(!s.actors.containsKey(incoming.getUUID()),"Fresh real creature is not added to frozen snapshot");
        ghostOnly.setPos(center.add(6,-.5,0));
        h.runAfterDelay(20,()->incoming.setPos(center.add(0,-.5,0)));
        h.runAfterDelay(29,()->h.assertTrue(incoming.getHealth()==100,"No hit before1.5 seconds"));
        h.runAfterDelay(31,()->{
            h.assertTrue(Math.abs(incoming.getHealth()-70)<.01,"At1.5 seconds hit the real incoming creature while still phased");
            h.assertTrue(ghostOnly.getHealth()==100&&miss.getHealth()==100,"Neither frozen old position nor broad-box near miss counts");
            h.assertTrue(s.slashes.isEmpty(),"Resolved slash frees its pending slot");PhaseManager.exit(p);
        });
        h.runAfterDelay(40,()->{h.assertTrue(incoming.getHealth()==70,"Exit and later ticks cannot repeat slash damage");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=80) public static void earlyExitKeepsOriginalDeadline(GameTestHelper h) {
        var p=player(h);var center=p.getEyePosition().add(0,0,2);var target=cow(h,center.add(0,-.5,0));
        PhaseManager.enter(p);slash(p);PhaseManager.exit(p);
        h.assertTrue(target.getHealth()==100,"Exiting must not settle a pending slash immediately");
        h.runAfterDelay(29,()->h.assertTrue(target.getHealth()==100,"Exit does not shorten the delay"));
        h.runAfterDelay(31,()->{h.assertTrue(Math.abs(target.getHealth()-70)<.01,"Pending slash still resolves in reality after exit");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=80) public static void reentryKeepsSlashDeadlinesSeparate(GameTestHelper h) {
        var p=player(h);PhaseManager.enter(p);var old=PhaseManager.get(p);slash(p);
        var oldId=old.slashes.keySet().iterator().next();PhaseManager.exit(p);
        h.runAfterDelay(15,()->{
            PhaseManager.enter(p);slash(p);var current=PhaseManager.get(p);
            h.assertTrue(!current.id.equals(old.id)&&!current.slashes.containsKey(oldId),"Reentry assigns new session and slash identities");
        });
        h.runAfterDelay(31,()->h.assertTrue(PhaseManager.get(p).slashes.size()==1,"Old deadline cannot remove the new session's pending slash"));
        h.runAfterDelay(46,()->{
            h.assertTrue(PhaseManager.get(p).slashes.isEmpty(),"New slash resolves at its own deadline");PhaseManager.exit(p);h.succeed();
        });
    }
}
