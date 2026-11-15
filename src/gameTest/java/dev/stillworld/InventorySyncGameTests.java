package dev.stillworld;

import dev.stillworld.core.Rank;
import dev.stillworld.server.PhaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;

@GameTestHolder(Stillworld.ID)
@PrefixGameTestTemplate(false)
public final class InventorySyncGameTests {
    @GameTest(template="empty") public static void rejectedCreativePickResendsAuthoritativeInventory(GameTestHelper h) {
        var packets=new ArrayList<Packet<?>>();var p=SessionGameTests.player(h,packets::add);
        p.setGameMode(GameType.CREATIVE);p.initInventoryMenu();SessionGameTests.equip(p,Rank.INFINITY);
        PhaseManager.enter(p);packets.clear();
        try {
            p.connection.handleSetCreativeModeSlot(new ServerboundSetCreativeModeSlotPacket(37,new ItemStack(Items.STONE,64)));
            h.assertTrue(p.getInventory().getItem(1).isEmpty(),"Blocked creative pick cannot modify the real inventory inside Stillworld");
            h.assertTrue(packets.stream().anyMatch(packet->packet instanceof ClientboundContainerSetContentPacket contents
                    &&contents.getContainerId()==p.inventoryMenu.containerId&&contents.getItems().get(37).isEmpty()),
                    "Rejected creative pick must correct the client's predicted inventory immediately");
        } finally {PhaseManager.exit(p);}
        h.succeed();
    }
    @GameTest(template="empty") public static void exitResyncsInventoryAndNormalCreativePlacementWorks(GameTestHelper h) {
        var packets=new ArrayList<Packet<?>>();var p=SessionGameTests.player(h,packets::add);
        p.setGameMode(GameType.CREATIVE);p.initInventoryMenu();SessionGameTests.equip(p,Rank.INFINITY);
        PhaseManager.enter(p);packets.clear();PhaseManager.exit(p);
        h.assertTrue(packets.stream().anyMatch(packet->packet instanceof ClientboundContainerSetContentPacket contents
                &&contents.getContainerId()==p.inventoryMenu.containerId&&contents.getItems().get(37).isEmpty()),
                "Exit must reconcile even previously stale client inventory");
        p.getInventory().selected=1;
        p.connection.handleSetCreativeModeSlot(new ServerboundSetCreativeModeSlotPacket(37,new ItemStack(Items.STONE,64)));
        h.assertTrue(p.getMainHandItem().is(Items.STONE),"Creative inventory updates work normally after exit");
        var support=h.absolutePos(new BlockPos(5,1,3));h.getLevel().setBlockAndUpdate(support,Blocks.DIRT.defaultBlockState());
        var hit=new BlockHitResult(Vec3.atCenterOf(support).add(0,.5,0),Direction.UP,support,false);
        p.connection.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND,hit,1));
        h.assertTrue(h.getLevel().getBlockState(support.above()).is(Blocks.STONE),"Real block placement survives server validation after exit");
        h.succeed();
    }
}
