package dev.stillworld;

import dev.stillworld.core.Rank;
import dev.stillworld.server.PhaseManager;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Stillworld.ID)
@PrefixGameTestTemplate(false)
public final class MovementGameTests {
    private static final Vec3 MOTION=new Vec3(.7,.12,-.4);

    @GameTest(template="empty") public static void entryPreservesFlightMovement(GameTestHelper h) {
        var player=SessionGameTests.player(h);SessionGameTests.equip(player,Rank.RANK1);
        var position=player.position();setFlight(player);
        try {
            h.assertTrue(PhaseManager.enter(player),"Equipped player enters phase");
            h.assertTrue(player.getDeltaMovement().equals(MOTION),"Visual entry must not zero flight velocity");
            assertMovement(h,player,position);
        } finally {PhaseManager.exit(player);}
        h.succeed();
    }

    @GameTest(template="empty") public static void clearExitPreservesMotionAndSendsNoTeleport(GameTestHelper h) {
        var player=SessionGameTests.player(h);SessionGameTests.equip(player,Rank.RANK1);
        h.assertTrue(PhaseManager.enter(player),"Equipped player enters phase");
        var connection=new RecordingListener(player);
        var position=player.position();setFlight(player);
        h.assertTrue(player.serverLevel().noCollision(player,player.getBoundingBox()),"Fixture has clear space");
        PhaseManager.exit(player);
        h.assertTrue(connection.positionPackets==0,"Clear exit must not send the absolute teleport that resets client velocity");
        h.assertTrue(player.getDeltaMovement().equals(MOTION),"Clear exit must preserve server velocity");
        assertMovement(h,player,position);
        h.assertTrue(PhaseManager.get(player)==null,"Movement preservation must not retain the phase session");
        h.succeed();
    }

    @GameTest(template="empty") public static void obstructedExitStillRelocatesSafely(GameTestHelper h) {
        var player=SessionGameTests.player(h);SessionGameTests.equip(player,Rank.RANK1);
        PhaseManager.enter(player);var position=player.position();
        var connection=new RecordingListener(player);setFlight(player);
        h.getLevel().setBlockAndUpdate(player.blockPosition(),Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(player.blockPosition().above(),Blocks.STONE.defaultBlockState());
        PhaseManager.exit(player);
        h.assertTrue(!player.position().equals(position),"A new real wall must trigger safety relocation");
        h.assertTrue(h.getLevel().noCollision(player,player.getBoundingBox()),"Safety relocation must land outside real blocks");
        h.assertTrue(connection.positionPackets==1,"Safety relocation sends exactly one actual position correction");
        h.assertTrue(player.getDeltaMovement().equals(Vec3.ZERO)&&player.fallDistance==0,"Safety relocation clears unsafe motion and fall distance");
        h.succeed();
    }

    private static void setFlight(ServerPlayer player) {
        player.getAbilities().mayfly=true;player.getAbilities().flying=true;
        player.setSprinting(true);player.fallDistance=3.25f;player.setDeltaMovement(MOTION);
    }
    private static void assertMovement(GameTestHelper h,ServerPlayer player,Vec3 position) {
        h.assertTrue(player.position().equals(position),"Clear switch preserves position");
        h.assertTrue(player.getAbilities().flying&&player.isSprinting(),"Clear switch preserves flying and sprint state");
        h.assertTrue(player.fallDistance==3.25f,"Clear switch does not reset accumulated fall distance");
    }
    /** Exercise vanilla teleport construction, recording its actual outgoing packet without a desktop client. */
    private static final class RecordingListener extends ServerGamePacketListenerImpl {
        private int positionPackets;
        RecordingListener(ServerPlayer player) {
            super(player.serverLevel().getServer(),new Connection(PacketFlow.SERVERBOUND),player,
                    CommonListenerCookie.createInitial(player.getGameProfile(),false));
        }
        @Override public void send(Packet<?> packet,PacketSendListener callback) {
            if(packet instanceof ClientboundPlayerPositionPacket)positionPackets++;
        }
    }
}
