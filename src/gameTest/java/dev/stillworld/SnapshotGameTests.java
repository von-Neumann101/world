package dev.stillworld;

import dev.stillworld.snapshot.SnapshotWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Stillworld.ID)
@PrefixGameTestTemplate(false)
public final class SnapshotGameTests {
    @GameTest(template = "empty") public static void snapshotSurvivesRealityChanges(GameTestHelper h) {
        BlockPos p = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
        var a = new SnapshotWorld(h.getLevel()); a.capture(h.getLevel().getChunkAt(p));
        h.getLevel().setBlockAndUpdate(p, Blocks.GOLD_BLOCK.defaultBlockState());
        h.assertTrue(a.getBlockState(p).is(Blocks.STONE), "frozen terrain must keep entry stone after reality changes");
        var b = new SnapshotWorld(h.getLevel()); b.capture(h.getLevel().getChunkAt(p));
        h.assertTrue(b.getBlockState(p).is(Blocks.GOLD_BLOCK), "later entry must see later world");
        a.capture(h.getLevel().getChunkAt(p));
        h.assertTrue(a.getBlockState(p).is(Blocks.STONE), "recapturing a loaded chunk must not refresh old snapshot");
        h.succeed();
    }
}
