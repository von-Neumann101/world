package dev.stillworld;

import com.mojang.authlib.GameProfile;
import dev.stillworld.core.Rank;
import dev.stillworld.server.PhaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import java.util.UUID;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

@GameTestHolder(Stillworld.ID)
@PrefixGameTestTemplate(false)
public final class SessionGameTests {
    static ServerPlayer player(GameTestHelper h) {
        return player(h,packet->{});
    }
    static ServerPlayer player(GameTestHelper h,java.util.function.Consumer<net.minecraft.network.protocol.Packet<?>> packets) {
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "still-test"), ClientInformation.createDefault());
        // Ordinary server actions now send resource/cooldown messages. Exercise those paths
        // with a real listener and a packet sink, without connecting a desktop client.
        new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),p,
                net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener callback) { packets.accept(packet); }
        };
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
        return p;
    }
    static void equip(ServerPlayer p, Rank rank) {
        var inventory = CuriosApi.getCuriosInventory(p).orElseThrow();
        var slot = inventory.getStacksHandler("stillworld").orElseThrow();
        slot.getStacks().setStackInSlot(0, new ItemStack(ModContent.CHARMS.get(rank).get()));
    }
    @GameTest(template = "empty") public static void accessoryGatesSessionAndExitCleans(GameTestHelper h) {
        var p = player(h);
        h.assertTrue(!PhaseManager.enter(p), "ordinary inventory cannot enter");
        p.getInventory().add(new ItemStack(ModContent.CHARMS.get(Rank.RANK6).get()));
        h.assertTrue(!PhaseManager.enter(p), "carrying a charm without equipping cannot enter");
        equip(p, Rank.RANK1);
        h.assertTrue(PhaseManager.enter(p), "functional Curios accessory permits entry");
        h.assertTrue(PhaseManager.isPhased(p), "entry creates an active session");
        PhaseManager.exit(p);
        h.assertTrue(!PhaseManager.isPhased(p), "exit removes active session");
        PhaseManager.exit(p);
        h.assertTrue(!PhaseManager.isPhased(p), "repeated exit is harmless");
        h.succeed();
    }
    @GameTest(template = "empty") public static void collisionUsesFrozenBlocks(GameTestHelper h) {
        var p = player(h); equip(p, Rank.RANK1);
        BlockPos wall = p.blockPosition().east();
        h.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(wall.above(), Blocks.STONE.defaultBlockState());
        PhaseManager.enter(p);
        h.getLevel().removeBlock(wall, false); h.getLevel().removeBlock(wall.above(), false);
        double start = p.getX(); p.move(MoverType.SELF, new Vec3(1, 0, 0));
        h.assertTrue(p.getX() - start < 0.3, "server movement must collide with the frozen wall removed from reality");
        h.assertTrue(!p.canBeSeenByAnyone(), "a phased player must fail normal mob visibility checks");
        PhaseManager.exit(p); h.succeed();
    }
    @GameTest(template = "empty") public static void wardenCannotSenseOrKeepTarget(GameTestHelper h) {
        var p=player(h);equip(p,Rank.RANK1);
        var warden=h.spawn(EntityType.WARDEN,new BlockPos(6,2,3));
        warden.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET,p);
        PhaseManager.enter(p);
        h.assertTrue(!warden.canTargetEntity(p),"warden sniff and anger must reject phased players");
        h.assertTrue(!warden.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET),"entry clears the warden brain attack target");
        PhaseManager.exit(p);h.succeed();
    }
}
