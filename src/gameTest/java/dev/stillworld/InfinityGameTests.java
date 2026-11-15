package dev.stillworld;

import dev.stillworld.core.Rank;
import dev.stillworld.server.PhaseManager;
import dev.stillworld.server.PsycheManager;
import dev.stillworld.server.PhaseSkills;
import dev.stillworld.network.PhaseNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Stillworld.ID)
@PrefixGameTestTemplate(false)
public final class InfinityGameTests {
    @GameTest(template="empty") public static void infinityCommandCreatesTestRank(GameTestHelper h) throws Exception {
        var p=SessionGameTests.player(h);
        int result=h.getLevel().getServer().getCommands().getDispatcher().execute("stillworld rank infinity",p.createCommandSourceStack().withPermission(2));
        h.assertTrue(result==1,"Operator can select infinity rank");
        h.assertTrue(ModContent.equippedRank(p)!=null&&ModContent.equippedRank(p).canSlash(),"Test rank includes rank6 skills");
        var stack=new ItemStack(ModContent.CHARMS.get(Rank.INFINITY).get());
        h.assertTrue(stack.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("curios","stillworld"))),"Infinity is accepted by the accessory slot tag");
        for(String name:new String[]{"inf","\u221e","\u267e\ufe0f"}) {
            h.assertTrue(h.getLevel().getServer().getCommands().getDispatcher().execute("stillworld rank "+name,p.createCommandSourceStack().withPermission(2))==1,"Command alias "+name);
            h.assertTrue(ModContent.equippedRank(p)==Rank.INFINITY,"Alias equips infinity");
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void creativeExitRefillsAnyRank(GameTestHelper h) {
        var p=SessionGameTests.player(h);p.setGameMode(GameType.CREATIVE);SessionGameTests.equip(p,Rank.RANK1);
        PhaseManager.enter(p);PsycheManager.setEnergy(p,7);PsycheManager.data(p).cooldownTicks=0;
        h.assertTrue(PhaseManager.requestExit(p),"Creative player exits normally");
        h.assertTrue(PsycheManager.data(p).energy==100,"All creative ranks refill on actual exit");
        PsycheManager.setEnergy(p,3);PhaseManager.exit(p);
        h.assertTrue(PsycheManager.data(p).energy==3,"No-op exit outside phase cannot refill");h.succeed();
    }

    private static void ticks(net.minecraft.server.level.ServerPlayer p,int count) {for(int i=0;i<count;i++){p.tickCount++;PsycheManager.tick(p);}}
    @GameTest(template="empty") public static void infinityDrainsAndSyncsExhaustionWithoutErosion(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.INFINITY);
        var listener=new StatusListener(p);PhaseManager.enter(p);var s=PhaseManager.get(p);
        try {
            ticks(p,40);h.assertTrue(PsycheManager.data(p).energy==99,"Infinity still drains near psyche");
            p.setPos(s.entry.add(65,0,0));ticks(p,40);h.assertTrue(PsycheManager.data(p).energy==97,"Infinity still doubles far drain");
            PsycheManager.setEnergy(p,0);ticks(p,240);
            h.assertTrue(p.getMaxHealth()==20&&s.exhaustedTicks==240,"Exhaustion remains observable without health erosion");
            h.assertTrue(listener.state.getInt("energy")==0&&listener.state.getBoolean("far")&&listener.state.getInt("exhaustedTicks")==240,"Visual client receives real depletion and danger");
            h.assertTrue(listener.state.getInt("cooldownTicks")==0,"Infinity HUD has no cooldown");
            h.assertTrue(PhaseManager.requestExit(p),"Depleted far infinity exits immediately");
            h.assertTrue(PsycheManager.data(p).energy==0&&PsycheManager.data(p).doomTicks==-1&&p.getMaxHealth()==20,"Survival infinity exit does not refill or punish low energy");
            PhaseManager.enter(p);p.setPos(PhaseManager.get(p).entry.add(65,0,0));PsycheManager.setEnergy(p,100);
            h.assertTrue(PhaseManager.requestExit(p)&&PsycheManager.data(p).energy==60,"Funded survival infinity still pays the far exit drain");
        } finally {PhaseManager.exit(p);}
        h.succeed();
    }
    @GameTest(template="empty") public static void infinityUsesAllSkillsAtZeroAndStillSpends(GameTestHelper h) {
        for(boolean far:new boolean[]{false,true}) {
            var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.INFINITY);var position=p.position();
            if(far)p.setPos(position.add(-65,0,0));
            var cow=h.spawnWithNoFreeWill(EntityType.COW,new BlockPos(5,2,3));cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);cow.setHealth(100);
            PhaseManager.enter(p);var s=PhaseManager.get(p);p.setPos(position);SkillGameTests.aim(p,cow.getBoundingBox().getCenter());
            try {
                PsycheManager.setEnergy(p,1);
                h.assertTrue(PhaseSkills.mark(s,cow.getUUID())&&PsycheManager.data(p).energy==0,"Low-energy infinity mark still consumes remaining psyche");
                h.assertTrue(PhaseSkills.attack(s,cow.getUUID()),"Zero-energy infinity can record damage");
                var slash=PhaseNetwork.message("slash");slash.putUUID("session",s.id);
                PhaseManager.handle(p,slash);h.assertTrue(s.slashes.isEmpty(),"Infinity still requires its slash weapon");
                p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModContent.BLADE.get()));
                PhaseManager.handle(p,slash);h.assertTrue(s.slashes.size()==1&&PsycheManager.data(p).energy==0,"Zero-energy slash succeeds without negative psyche");
                PsycheManager.setEnergy(p,100);PhaseManager.handle(p,slash);
                h.assertTrue(PsycheManager.data(p).energy==(far?20:60),"Funded infinity slashes still pay normal/doubled costs");
            } finally {PhaseManager.exit(p);cow.discard();}
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void infinityIgnoresOldCooldownAndAllowsRepeatedSwitches(GameTestHelper h) {
        var p=SessionGameTests.player(h);SessionGameTests.equip(p,Rank.INFINITY);PsycheManager.data(p).cooldownTicks=1400;
        var listener=new StatusListener(p);PsycheManager.sync(p);
        h.assertTrue(listener.state.getInt("cooldownTicks")==0,"Equipped infinity hides previous ordinary cooldown");
        for(int i=0;i<3;i++) {
            h.assertTrue(PhaseManager.toggle(p)&&PhaseManager.get(p)!=null,"Infinity enters without waiting");
            h.assertTrue(PhaseManager.toggle(p)&&PhaseManager.get(p)==null,"Infinity leaves without waiting");
        }
        h.assertTrue(PsycheManager.data(p).cooldownTicks==0,"Infinity switches do not start a timer");
        SessionGameTests.equip(p,Rank.RANK6);PhaseManager.enter(p);
        h.assertTrue(!PhaseManager.requestExit(p),"Ordinary rank still has cooldown");PhaseManager.exit(p);h.succeed();
    }
    @GameTest(template="empty") public static void creativeFarCleanupRefillsAfterFee(GameTestHelper h) {
        for(Rank rank:new Rank[]{Rank.RANK6,Rank.INFINITY}) {
            var p=SessionGameTests.player(h);p.setGameMode(GameType.CREATIVE);SessionGameTests.equip(p,rank);PhaseManager.enter(p);
            p.setPos(PhaseManager.get(p).entry.add(65,0,0));PsycheManager.setEnergy(p,rank==Rank.INFINITY?0:61);
            PhaseManager.exitWithCost(p);
            h.assertTrue(PsycheManager.data(p).energy==100&&PsycheManager.data(p).doomTicks==-1,"Creative cleanup refills after normal or infinity settlement");
            h.assertTrue(p.getMaxHealth()==20,"Funded normal/infinity creative exit preserves health");
        }
        h.succeed();
    }
    private static final class StatusListener extends net.minecraft.server.network.ServerGamePacketListenerImpl {
        net.minecraft.nbt.CompoundTag state=new net.minecraft.nbt.CompoundTag();
        StatusListener(net.minecraft.server.level.ServerPlayer p) {
            super(p.getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),p,
                    net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false));
        }
        @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener callback) {
            if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                    &&custom.payload() instanceof dev.stillworld.network.StatePayload payload&&payload.data().getString("op").equals("psyche"))state=payload.data().copy();
        }
    }
}
