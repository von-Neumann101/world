package dev.stillworld;

import dev.stillworld.core.Rank;
import dev.stillworld.network.PhaseNetwork;
import dev.stillworld.server.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Stillworld.ID)
@PrefixGameTestTemplate(false)
public final class PsycheGameTests {
    @GameTest(template="empty") public static void manualSwitchHasCooldown(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK1);
        var toggle=PhaseNetwork.message("toggle");
        PhaseManager.handle(p,toggle);
        try {
            h.assertTrue(PhaseManager.get(p)!=null,"First toggle enters");
            PhaseManager.handle(p,toggle);
            h.assertTrue(PhaseManager.get(p)!=null,"Cannot switch again before70 seconds");
        } finally { PhaseManager.exit(p); }
        h.succeed();
    }

    private static void ticks(ServerPlayer p,int count) { for(int i=0;i<count;i++){p.tickCount++;PsycheManager.tick(p);} }
    private static void close(GameTestHelper h,double actual,double expected,String name) {h.assertTrue(Math.abs(actual-expected)<.001,name+": "+actual+" expected "+expected);}
    private static void connection(ServerPlayer p) {
        new net.minecraft.server.network.ServerGamePacketListenerImpl(p.getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),p,
                net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener callback) {}
        };
    }
    @GameTest(template="empty") public static void drainAndCooldownTiming(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK1);
        h.assertTrue(PsycheManager.data(p).energy==100,"Initial psyche100");
        PsycheManager.setEnergy(p,50);ticks(p,80);
        h.assertTrue(PsycheManager.data(p).energy==50,"Reality does not regenerate psyche");
        PhaseManager.enter(p);var s=PhaseManager.get(p);
        try {
            ticks(p,39);h.assertTrue(PsycheManager.data(p).energy==50,"No early passive cost");
            ticks(p,1);h.assertTrue(PsycheManager.data(p).energy==49,"Near drain1 per40 ticks");
            p.setPos(s.entry.add(65,0,0));ticks(p,40);
            h.assertTrue(PsycheManager.data(p).energy==47,"Far drain doubles");
            p.setPos(s.entry);ticks(p,1319);
            h.assertTrue(!PhaseManager.requestExit(p),"1399 ticks is still on cooldown");
            ticks(p,1);h.assertTrue(PhaseManager.requestExit(p),"1400 ticks allows exit");
            h.assertTrue(!PhaseManager.enter(p),"Exit restarts the switch timer");
        } finally {PhaseManager.exit(p);}
        h.succeed();
    }
    @GameTest(template="empty") public static void exhaustionErodesAfterGrace(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK1);PhaseManager.enter(p);
        try {
            PsycheManager.setEnergy(p,0);ticks(p,199);close(h,p.getMaxHealth(),20,"Ten-second grace");
            ticks(p,1);close(h,p.getMaxHealth(),19,"First half-heart after ten seconds");
            ticks(p,39);close(h,p.getMaxHealth(),19,"Two-second erosion interval");
            ticks(p,1);close(h,p.getMaxHealth(),18,"Next half-heart");
            PsycheManager.setEnergy(p,100);ticks(p,80);close(h,p.getMaxHealth(),18,"Refilling stops erosion but does not heal loss");
            var tag=p.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            var loaded=SessionGameTests.player(h);loaded.load(tag);
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(loaded));
            close(h,loaded.getMaxHealth(),18,"Save/load/login preserves penalty");
            h.assertTrue(PsycheManager.data(loaded).energy==98,"Save/load preserves psyche");
        } finally {PhaseManager.exit(p);}
        h.succeed();
    }
    @GameTest(template="empty") public static void farExitPaysFortyAndUnderfundedExitKills(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK1);PhaseManager.enter(p);
        var s=PhaseManager.get(p);p.setPos(s.entry.add(65,0,0));PsycheManager.data(p).cooldownTicks=0;
        PsycheManager.setEnergy(p,40);h.assertTrue(PhaseManager.requestExit(p),"Funded exit succeeds");
        h.assertTrue(PsycheManager.data(p).energy==0&&PsycheManager.data(p).doomTicks==-1,"Fee is final40, no penalty at exactly40");
        close(h,p.getMaxHealth(),20,"Funded exit preserves maximum");
        PsycheManager.data(p).cooldownTicks=0;PhaseManager.enter(p);s=PhaseManager.get(p);p.setPos(s.entry.add(65,0,0));
        PsycheManager.setEnergy(p,39);PsycheManager.data(p).cooldownTicks=0;PhaseManager.requestExit(p);
        close(h,p.getMaxHealth(),12,"Underfunded exit loses40 percent");
        h.assertTrue(PsycheManager.data(p).energy==0,"Underfunded exit spends remaining energy");
        connection(p);ticks(p,799);h.assertTrue(p.isAlive(),"Alive until forty-second deadline");
        ticks(p,1);h.assertTrue(!p.isAlive(),"Ordinary death at forty seconds");
        close(h,p.getMaxHealth(),12,"Reality death does not also lose phase50 percent");
        h.assertTrue(PsycheManager.data(p).doomTicks==-1,"Death consumes curse once");h.succeed();
    }
    @GameTest(template="empty") public static void phaseDeathCopiesPenaltyAndRespectsOtherModifiers(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK1);
        var bonus=Stillworld.id("test_health_bonus");
        p.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier(bonus,10,AttributeModifier.Operation.ADD_VALUE));
        p.setHealth(30);PsycheManager.setEnergy(p,73);PhaseManager.enter(p);connection(p);PsycheManager.data(p).cooldownTicks=700;
        p.hurt(p.damageSources().genericKill(),Float.MAX_VALUE);
        h.assertTrue(!p.isAlive(),"Phase death is not canceled");close(h,p.getMaxHealth(),15,"50 percent of30");
        h.assertTrue(p.getAttribute(Attributes.MAX_HEALTH).getModifier(bonus)!=null,"Unrelated modifier remains");
        // Immediate respawn may clone before the dead owner's next tick cleans its session.
        var clone=SessionGameTests.player(h);clone.setUUID(p.getUUID());connection(clone);clone.restoreFrom(p,false);
        h.assertTrue(PhaseManager.get(clone)==null,"Actual respawn must detach the old same-UUID phase session");
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(clone,false));
        close(h,PsycheManager.data(clone).healthScale,.5,"Attachment copied by actual respawn path");
        h.assertTrue(PsycheManager.data(clone).cooldownTicks==1400,"Clone copies the cleanup timer after it resets");
        close(h,clone.getMaxHealth(),10,"Respawn applies loss to normal base maximum");
        h.assertTrue(PsycheManager.data(clone).energy==73,"Respawn does not fabricate psyche refill");
        h.succeed();
    }
    @GameTest(template="empty") public static void skillsChargeOnlyAcceptedActions(GameTestHelper h) {
        for(boolean far:new boolean[]{false,true}) {
            var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK6);
            var entry=p.position();if(far)p.setPos(entry.add(-65,0,0));
            var cow=h.spawnWithNoFreeWill(EntityType.COW,new BlockPos(5,2,3));cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);cow.setHealth(100);
            PhaseManager.enter(p);var s=PhaseManager.get(p);p.setPos(entry);SkillGameTests.aim(p,cow.getBoundingBox().getCenter());
            try {
                int multiplier=far?2:1;
                h.assertTrue(PhaseSkills.mark(s,cow.getUUID()),"Valid mark");
                h.assertTrue(PsycheManager.data(p).energy==100-20*multiplier,"Mark cost by zone");
                PhaseSkills.mark(s,cow.getUUID());h.assertTrue(PsycheManager.data(p).energy==100-20*multiplier,"Repeat mark is free");
                h.assertTrue(PhaseSkills.attack(s,cow.getUUID()),"Valid attack");
                h.assertTrue(PsycheManager.data(p).energy==100-24*multiplier,"Attack cost by zone");
                var slash=PhaseNetwork.message("slash");slash.putUUID("session",s.id);
                PhaseManager.handle(p,slash);h.assertTrue(s.slashes.isEmpty(),"Wrong weapon cannot slash");
                p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModContent.BLADE.get()));PsycheManager.setEnergy(p,100);
                PhaseManager.handle(p,slash);h.assertTrue(PsycheManager.data(p).energy==100-40*multiplier,"Slash cost by zone");
                PsycheManager.setEnergy(p,0);PhaseManager.handle(p,slash);
                h.assertTrue(s.slashes.size()==1&&!PhaseSkills.attack(s,cow.getUUID()),"Insufficient energy blocks skills");
            } finally {PhaseManager.exit(p);cow.discard();}
        }
        h.succeed();
    }

    @GameTest(template="empty") public static void cleanupChargesWithoutStrandingAndTimersPersist(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK1);PhaseManager.enter(p);
        var s=PhaseManager.get(p);p.setPos(s.entry.add(65,0,0));PsycheManager.setEnergy(p,10);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(p));
        h.assertTrue(PhaseManager.get(p)==null,"Logout cleans up despite switch cooldown");
        close(h,p.getMaxHealth(),12,"Logout does not evade far exit cost");
        var saved=p.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        var loaded=SessionGameTests.player(h);loaded.load(saved);NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(loaded));
        h.assertTrue(PsycheManager.data(loaded).doomTicks==800&&PsycheManager.data(loaded).cooldownTicks==1400,"Login retains countdowns");
        ticks(loaded,40);h.assertTrue(PsycheManager.data(loaded).doomTicks==760,"Doom continues outside phase");
        loaded.hurt(loaded.damageSources().genericKill(),Float.MAX_VALUE);
        h.assertTrue(PsycheManager.data(loaded).doomTicks==-1,"Earlier ordinary death consumes doom");
        close(h,loaded.getMaxHealth(),12,"Reality death does not duplicate health loss");h.succeed();
    }
    @GameTest(template="empty") public static void canceledDeathDoesNotDeductMaximum(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK1);PhaseManager.enter(p);
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDeathEvent> cancel=e->{if(e.getEntity()==p){e.setCanceled(true);p.setHealth(1);}};
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH,cancel);
        try {
            p.hurt(p.damageSources().genericKill(),Float.MAX_VALUE);
            h.assertTrue(p.isAlive(),"Fixture cancels death");close(h,p.getMaxHealth(),20,"Canceled death has no phase penalty");
        } finally {NeoForge.EVENT_BUS.unregister(cancel);PhaseManager.exit(p);}
        h.succeed();
    }
    @GameTest(template="empty") public static void commandsShareGatesAndRequirePermission(GameTestHelper h) throws Exception {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK1);
        var dispatcher=h.getLevel().getServer().getCommands().getDispatcher();var source=p.createCommandSourceStack();
        h.assertTrue(dispatcher.execute("stillworld toggle",source)==1,"Toggle command enters");
        h.assertTrue(dispatcher.execute("stillworld exit",source)==0&&PhaseManager.get(p)!=null,"Exit command cannot bypass cooldown");
        h.assertTrue(dispatcher.parse("stillworld psyche set 0",source.withPermission(0)).getReader().canRead(),"Non-operator cannot access psyche command");
        dispatcher.execute("stillworld psyche set 0",source.withPermission(2));
        h.assertTrue(PsycheManager.data(p).energy==0,"Operator can set psyche");
        dispatcher.execute("stillworld cooldown reset",source.withPermission(2));
        h.assertTrue(dispatcher.execute("stillworld exit",source)==1,"Reset permits test exit");h.succeed();
    }

    @GameTest(template="empty") public static void liveEndCloneSettlesFarCostBeforeCopy(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.RANK1);PhaseManager.enter(p);
        p.setPos(PhaseManager.get(p).entry.add(65,0,0));PsycheManager.setEnergy(p,39);
        // Vanilla removes the healthy old entity before its live End-return clone is made.
        p.remove(net.minecraft.world.entity.Entity.RemovalReason.CHANGED_DIMENSION);
        var clone=SessionGameTests.player(h);clone.setUUID(p.getUUID());clone.restoreFrom(p,true);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(clone,true));
        h.assertTrue(PhaseManager.get(clone)==null,"Live clone clears the old session");
        h.assertTrue(PsycheManager.data(clone).energy==0&&PsycheManager.data(clone).doomTicks==800,"Live clone pays far cost despite old entity removal");
        close(h,clone.getMaxHealth(),12,"Live return copies only40 percent exit loss, no phase death50 percent");h.succeed();
    }
}
