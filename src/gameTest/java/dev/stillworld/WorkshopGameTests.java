package dev.stillworld;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import dev.stillworld.workshop.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@GameTestHolder(Stillworld.ID)
@PrefixGameTestTemplate(false)
public final class WorkshopGameTests {
    @GameTest(template="empty")
    public static void explorationContentExists(GameTestHelper h) {
        for (String name : new String[]{"anchor_recorder", "unstable_stillworld_material", "stable_stillworld_material",
                "stillworld_ore", "deepslate_stillworld_ore", "distortion_stabilizer"}) {
            h.assertTrue(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Stillworld.ID, name)) != Items.AIR,
                    "Missing exploration/workshop item: " + name);
        }
        h.succeed();
    }

    @GameTest(template="empty") public static void recorderDeduplicatesDimensionChecksAndSaves(GameTestHelper h) {
        var stack = new ItemStack(WorkshopContent.ANCHOR_RECORDER.get());
        var biomes = h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        var mountain = biomes.getOrThrow(Biomes.JAGGED_PEAKS);
        AnchorRecorderItem.record(stack, Level.NETHER, mountain);
        h.assertTrue(AnchorRecorderItem.visited(stack) == 0, "Wrong dimension cannot record Overworld mountain");
        AnchorRecorderItem.record(stack, Level.OVERWORLD, mountain);
        AnchorRecorderItem.record(stack, Level.OVERWORLD, mountain);
        h.assertTrue(AnchorRecorderItem.visited(stack) == 1, "Repeated mountain stays one category");
        var keys = java.util.List.of(Biomes.DESERT, Biomes.OCEAN, Biomes.FOREST,
                Biomes.WARPED_FOREST, Biomes.CRIMSON_FOREST, Biomes.THE_END, Biomes.END_HIGHLANDS);
        for (int i = 0; i < keys.size(); i++) {
            var biome = biomes.getOrThrow(keys.get(i));
            AnchorRecorderItem.record(stack, i < 3 ? Level.OVERWORLD : i < 5 ? Level.NETHER : Level.END, biome);
        }
        h.assertTrue(AnchorRecorderItem.isFull(stack) && stack.hasFoil(), "All eight distinct dimension-specific categories fully charge and glow");
        var restored = ItemStack.parse(h.getLevel().registryAccess(), stack.save(h.getLevel().registryAccess())).orElseThrow();
        h.assertTrue(AnchorRecorderItem.isFull(restored), "Recorder data component survives item serialization");
        h.assertTrue(AnchorRecorderItem.visited(new ItemStack(WorkshopContent.ANCHOR_RECORDER.get())) == 0, "Progress belongs to each item");
        h.succeed();
    }

    @GameTest(template="empty") public static void allRotationsAuthorizeAndAssignDistinctDisplays(GameTestHelper h) {
        var center = h.absolutePos(new BlockPos(4, 2, 4));
        var player = SessionGameTests.player(h);
        for (int rotation = 0; rotation < 4; rotation++) {
            var machine = build(h, center, rotation);
            machine.refreshStructure();
            h.assertTrue(!machine.active(), "Complete structure alone does not authorize processing/displays");
            h.assertTrue(!machine.activate(new ItemStack(WorkshopContent.ANCHOR_RECORDER.get()), player), "Incomplete recorder rejected");
            var recorder = fullRecorder();
            h.assertTrue(machine.activate(recorder, player) && machine.authorized() && machine.active(), "Rotation " + rotation + " accepts full recorder");
            h.assertTrue(recorder.getCount() == 1 && AnchorRecorderItem.isFull(recorder), "Activation does not consume recorder or charge");
            for (int i = 0; i < 8; i++) {
                var auxiliary = (AuxiliaryBlockEntity) h.getLevel().getBlockEntity(auxiliary(center, i, rotation));
                h.assertTrue(auxiliary.biomeIndex() == i && center.equals(auxiliary.owner()), "Rotation maps each display to its deterministic category");
            }
            h.getLevel().setBlockAndUpdate(center, Blocks.AIR.defaultBlockState());
            h.assertTrue(((AuxiliaryBlockEntity) h.getLevel().getBlockEntity(auxiliary(center, 0, rotation))).biomeIndex() == -1,
                    "Replacing the machine immediately clears its owned displays");
        }
        h.succeed();
    }

    @GameTest(template="empty") public static void invalidMixedMissingAndDifferentHeightStructuresReject(GameTestHelper h) {
        var center = h.absolutePos(new BlockPos(4, 2, 4));
        var player = SessionGameTests.player(h);
        var machine = build(h, center, 0);
        var diagonal = auxiliary(center, 4, 0);
        h.getLevel().setBlockAndUpdate(diagonal, WorkshopContent.END_AUXILIARY.get().defaultBlockState());
        h.assertTrue(!machine.activate(fullRecorder(), player), "Mixed dimension auxiliary layout rejected");
        h.getLevel().setBlockAndUpdate(diagonal, WorkshopContent.NETHER_AUXILIARY.get().defaultBlockState());
        var cardinal = auxiliary(center, 0, 0);
        h.getLevel().setBlockAndUpdate(cardinal, Blocks.AIR.defaultBlockState());
        h.getLevel().setBlockAndUpdate(cardinal.above(), WorkshopContent.OVERWORLD_AUXILIARY.get().defaultBlockState());
        h.assertTrue(!machine.activate(fullRecorder(), player) && !machine.authorized(), "Missing same-Y cardinal cannot be replaced by a raised auxiliary");
        h.getLevel().setBlockAndUpdate(cardinal, WorkshopContent.OVERWORLD_AUXILIARY.get().defaultBlockState());
        h.getLevel().setBlockAndUpdate(center.north(), Blocks.STONE.defaultBlockState());
        h.assertTrue(machine.activate(fullRecorder(), player), "Intervening blocks are permitted");
        h.succeed();
    }

    @GameTest(template="empty") public static void exactProcessingPauseRepairAndOutputLimits(GameTestHelper h) {
        var center = h.absolutePos(new BlockPos(4, 2, 4));
        var machine = build(h, center, 0);
        machine.activate(fullRecorder(), SessionGameTests.player(h));
        machine.setItem(0, new ItemStack(WorkshopContent.UNSTABLE_MATERIAL.get(), 2));
        ticks(machine, 100);
        var broken = auxiliary(center, 4, 0);
        h.getLevel().setBlockAndUpdate(broken, Blocks.AIR.defaultBlockState());
        ticks(machine, 300);
        h.assertTrue(machine.progress() == 100 && machine.getItem(1).isEmpty() && machine.authorized() && !machine.active(), "Structure break pauses progress and retains authorization");
        h.assertTrue(((AuxiliaryBlockEntity) h.getLevel().getBlockEntity(auxiliary(center, 0, 0))).biomeIndex() == -1, "Structure break disables all remaining displays");
        h.getLevel().setBlockAndUpdate(broken, WorkshopContent.NETHER_AUXILIARY.get().defaultBlockState());
        ticks(machine, 99);
        h.assertTrue(machine.progress() == 199 && machine.getItem(1).isEmpty(), "No output before exactly 200 valid server ticks");
        ticks(machine, 1);
        h.assertTrue(machine.progress() == 0 && machine.getItem(0).getCount() == 1 && machine.getItem(1).is(WorkshopContent.STABLE_MATERIAL.get())
                && machine.getItem(1).getCount() == 1, "Two hundredth tick converts exactly one input to one stable material without fuel");
        machine.setItem(1, new ItemStack(WorkshopContent.STABLE_MATERIAL.get(), 64));
        ticks(machine, 300);
        h.assertTrue(machine.progress() == 0 && machine.getItem(0).getCount() == 1 && machine.getItem(1).getCount() == 64, "Full output cannot overstack or consume input");
        machine.setItem(1, new ItemStack(Items.DIRT));
        ticks(machine, 300);
        h.assertTrue(machine.progress() == 0 && machine.getItem(0).getCount() == 1, "Wrong output blocks processing");
        machine.setItem(1, ItemStack.EMPTY);
        machine.setItem(0, new ItemStack(Items.DIRT));
        ticks(machine, 300);
        h.assertTrue(machine.progress() == 0 && machine.getItem(1).isEmpty(), "Only unstable material processes");
        h.succeed();
    }

    @GameTest(template="empty") public static void saveLoadAndBreakingDropsInventoryAndClearsDisplays(GameTestHelper h) {
        var center = h.absolutePos(new BlockPos(4, 2, 4));
        var machine = build(h, center, 2);
        machine.activate(fullRecorder(), SessionGameTests.player(h));
        machine.setItem(0, new ItemStack(WorkshopContent.UNSTABLE_MATERIAL.get(), 3));
        machine.setItem(1, new ItemStack(WorkshopContent.STABLE_MATERIAL.get(), 2));
        ticks(machine, 73);
        var saved = machine.saveWithFullMetadata(h.getLevel().registryAccess());
        var restored = new StabilizerBlockEntity(center, machine.getBlockState());
        restored.loadWithComponents(saved, h.getLevel().registryAccess());
        h.getLevel().setBlockEntity(restored);
        h.assertTrue(restored.authorized() && restored.progress() == 73 && restored.getItem(0).getCount() == 3 && restored.getItem(1).getCount() == 2,
                "Authorization, progress and both inventory slots survive save/load");
        ticks(restored, 127);
        h.assertTrue(restored.getItem(0).getCount() == 2 && restored.getItem(1).getCount() == 3, "Loaded authorized machine resumes without recorder");
        h.getLevel().setBlockAndUpdate(center, Blocks.AIR.defaultBlockState());
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(1));
        int unstable = drops.stream().filter(e -> e.getItem().is(WorkshopContent.UNSTABLE_MATERIAL.get())).mapToInt(e -> e.getItem().getCount()).sum();
        int stable = drops.stream().filter(e -> e.getItem().is(WorkshopContent.STABLE_MATERIAL.get())).mapToInt(e -> e.getItem().getCount()).sum();
        h.assertTrue(unstable == 2 && stable == 3 && restored.isEmpty(), "Breaking drops all stored input/output exactly once");
        for (int i = 0; i < 8; i++) h.assertTrue(((AuxiliaryBlockEntity) h.getLevel().getBlockEntity(auxiliary(center, i, 2))).biomeIndex() == -1,
                "Breaking center releases every owned display");
        h.succeed();
    }

    @GameTest(template="empty") public static void overlappingCentersCannotStealActiveAuxiliary(GameTestHelper h) {
        var center = h.absolutePos(new BlockPos(4, 2, 4));
        var first = build(h, center, 0);
        var second = build(h, center.east(4), 0);
        var player = SessionGameTests.player(h);
        h.assertTrue(first.activate(fullRecorder(), player), "First overlapping center can activate");
        var shared = (AuxiliaryBlockEntity) h.getLevel().getBlockEntity(center.east(2));
        h.assertTrue(!second.activate(fullRecorder(), player) && !second.active(), "Second center rejects shared auxiliary owned by active first center");
        h.assertTrue(center.equals(shared.owner()) && shared.biomeIndex() == 1 && first.active(), "Rejected second activation preserves first owner and biome mapping");
        h.getLevel().setBlockAndUpdate(center, Blocks.AIR.defaultBlockState());
        h.assertTrue(second.activate(fullRecorder(), player), "Removing original center releases ownership for new activation");
        h.succeed();
    }

    @GameTest(template="empty") public static void validationNeverLoadsMissingChunks(GameTestHelper h) {
        var remote = new BlockPos(1_000_000, 80, 1_000_000);
        var machine = new StabilizerBlockEntity(remote, WorkshopContent.STABILIZER.get().defaultBlockState());
        machine.setLevel(h.getLevel());
        h.assertTrue(!h.getLevel().hasChunkAt(remote), "Remote validation fixture starts unloaded");
        int chunks = h.getLevel().getChunkSource().getLoadedChunksCount();
        h.assertTrue(!machine.activate(fullRecorder(), SessionGameTests.player(h)) && !machine.refreshStructure(), "Unloaded layout cannot activate");
        h.assertTrue(h.getLevel().getChunkSource().getLoadedChunksCount() == chunks && !h.getLevel().hasChunkAt(remote), "Validation never loads missing chunks");
        h.succeed();
    }

    @GameTest(template="empty") public static void shiftRecorderActivationAndMenuTransfersAreValidated(GameTestHelper h) {
        var center = h.absolutePos(new BlockPos(4, 2, 4));
        var machine = build(h, center, 0);
        var player = SessionGameTests.player(h);
        // Opening a real NeoForge menu queries negotiated connection attributes;
        // the general packet-sink fixture has no channel. Supply an in-memory
        // channel while retaining the real player, interaction path and menu.
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        var channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.ChannelAttributes.setConnectionType(connection,
                net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener callback) { }
        };
        try {
        player.setPos(Vec3.atCenterOf(center).add(0, 1, 2));
        player.setItemInHand(InteractionHand.MAIN_HAND, fullRecorder());
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
        player.setShiftKeyDown(true);
        var hit = new BlockHitResult(Vec3.atCenterOf(center), Direction.UP, center, false);
        player.gameMode.useItemOn(player, h.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        h.assertTrue(machine.authorized() && player.getMainHandItem().getCount() == 1, "Real sneak-right-click path authorizes without consumption even with occupied offhand");
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.gameMode.useItemOn(player, h.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
        h.assertTrue(player.containerMenu instanceof StabilizerMenu, "Ordinary interaction opens server menu");
        var menu = (StabilizerMenu) player.containerMenu;
        h.assertTrue(menu.stillValid(player) && menu.getSlot(0).mayPlace(new ItemStack(WorkshopContent.UNSTABLE_MATERIAL.get()))
                && !menu.getSlot(0).mayPlace(new ItemStack(Items.DIRT)) && !menu.getSlot(1).mayPlace(new ItemStack(WorkshopContent.STABLE_MATERIAL.get())), "Menu validates input and output slots");
        player.getInventory().setItem(9, new ItemStack(WorkshopContent.UNSTABLE_MATERIAL.get(), 5));
        menu.quickMoveStack(player, 2);
        h.assertTrue(machine.getItem(0).getCount() == 5 && player.getInventory().getItem(9).isEmpty(), "Shift transfer routes unstable material into input");
        machine.setItem(1, new ItemStack(WorkshopContent.STABLE_MATERIAL.get(), 3));
        menu.quickMoveStack(player, 1);
        h.assertTrue(machine.getItem(1).isEmpty() && player.getInventory().countItem(WorkshopContent.STABLE_MATERIAL.get()) == 3, "Shift transfer extracts output");
        player.setPos(Vec3.atCenterOf(center).add(20, 0, 0));
        h.assertTrue(!menu.stillValid(player), "Machine menu rejects distant players");
        h.succeed();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @GameTest(template="empty") public static void oreLootHonorsRealFortuneAndSilkTouchTools(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(3, 2, 3));
        var enchantments = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var silk = new ItemStack(Items.DIAMOND_PICKAXE);
        silk.enchant(enchantments.getOrThrow(Enchantments.SILK_TOUCH), 1);
        var fortune = new ItemStack(Items.DIAMOND_PICKAXE);
        fortune.enchant(enchantments.getOrThrow(Enchantments.FORTUNE), 3);
        var plain = new ItemStack(Items.DIAMOND_PICKAXE);
        for (Block ore : new Block[]{WorkshopContent.ORE.get(), WorkshopContent.DEEPSLATE_ORE.get()}) {
            var state = ore.defaultBlockState();
            var silkDrops = Block.getDrops(state, h.getLevel(), pos, null, null, silk);
            h.assertTrue(silkDrops.size() == 1 && silkDrops.getFirst().is(ore.asItem()) && silkDrops.getFirst().getCount() == 1, "Silk Touch returns the matching ore form");
            boolean fortuneBoosted = false;
            for (int i = 0; i < 100; i++) {
                var base = Block.getDrops(state, h.getLevel(), pos, null, null, plain);
                h.assertTrue(base.size() == 1 && base.getFirst().is(WorkshopContent.UNSTABLE_MATERIAL.get()) && base.getFirst().getCount() >= 1 && base.getFirst().getCount() <= 3,
                        "Unenchanted ore drops one to three unstable materials");
                var bonus = Block.getDrops(state, h.getLevel(), pos, null, null, fortune);
                h.assertTrue(bonus.size() == 1 && bonus.getFirst().is(WorkshopContent.UNSTABLE_MATERIAL.get()) && bonus.getFirst().getCount() >= 1 && bonus.getFirst().getCount() <= 12,
                        "Fortune uses vanilla ore multiplier with bounded base count");
                fortuneBoosted |= bonus.getFirst().getCount() > 3;
            }
            h.assertTrue(fortuneBoosted, "Real Fortune III tool increases possible material yield");
        }
        h.succeed();
    }

    @GameTest(template="empty") public static void oreFamiliesMatchVanillaHeightAndTwentyPercentAttempts(GameTestHelper h) throws Exception {
        for (String suffix : new String[]{"small", "medium", "large", "buried"}) {
            String vanilla = suffix.equals("small") ? "ore_diamond" : "ore_diamond_" + suffix;
            var baseline = resource(h, "minecraft", "worldgen/placed_feature/" + vanilla + ".json");
            var custom = resource(h, "stillworld", "worldgen/placed_feature/ore_stillworld_" + suffix + ".json");
            h.assertTrue(baseline.getAsJsonArray("placement").get(2).equals(custom.getAsJsonArray("placement").get(2)), "Same exact vanilla diamond height provider for " + suffix);
            double vanillaAttempts = switch (suffix) { case "small" -> 7; case "medium" -> 2; case "large" -> 1.0 / 9; default -> 4; };
            var distribution = custom.getAsJsonArray("placement").get(0).getAsJsonObject().getAsJsonObject("count").getAsJsonArray("distribution");
            double sum = 0, weight = 0;
            for (var entry : distribution) { var e = entry.getAsJsonObject(); sum += e.get("data").getAsDouble() * e.get("weight").getAsDouble(); weight += e.get("weight").getAsDouble(); }
            h.assertTrue(Math.abs(sum / weight / vanillaAttempts - 1.2) < 0.000001, "Exactly twenty percent more expected vein attempts for " + suffix);
            var vanillaConfig = resource(h, "minecraft", "worldgen/configured_feature/ore_diamond_" + suffix + ".json").getAsJsonObject("config");
            var customConfig = resource(h, "stillworld", "worldgen/configured_feature/ore_stillworld_" + suffix + ".json").getAsJsonObject("config");
            h.assertTrue(vanillaConfig.get("size").equals(customConfig.get("size")) && vanillaConfig.get("discard_chance_on_air_exposure").equals(customConfig.get("discard_chance_on_air_exposure")), "Vanilla vein sizes and exposure chances retained");
        }
        var modifier = resource(h, "stillworld", "neoforge/biome_modifier/add_stillworld_ore.json");
        h.assertTrue(modifier.get("biomes").getAsString().equals("#minecraft:is_overworld"), "Ore generation is limited to Overworld biomes");
        h.succeed();
    }

    private static JsonObject resource(GameTestHelper h, String namespace, String path) throws Exception {
        try (var reader = h.getLevel().getServer().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(namespace, path)).orElseThrow().openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
    private static ItemStack fullRecorder() {
        var stack = new ItemStack(WorkshopContent.ANCHOR_RECORDER.get());
        stack.set(WorkshopContent.ANCHOR_VISITS.get(), 255);
        return stack;
    }
    private static BlockPos auxiliary(BlockPos center, int index, int rotation) {
        int[][] offsets = {{0,-2},{2,0},{0,2},{-2,0},{-1,-1},{1,-1},{1,1},{-1,1}};
        int x = offsets[index][0], z = offsets[index][1];
        for (int i = 0; i < rotation; i++) { int next = -z; z = x; x = next; }
        return center.offset(x, 0, z);
    }
    private static StabilizerBlockEntity build(GameTestHelper h, BlockPos center, int rotation) {
        h.getLevel().setBlockAndUpdate(center, Blocks.AIR.defaultBlockState());
        h.getLevel().setBlockAndUpdate(center, WorkshopContent.STABILIZER.get().defaultBlockState());
        for (int i = 0; i < 8; i++) {
            var block = i < 4 ? WorkshopContent.OVERWORLD_AUXILIARY.get() : i < 6 ? WorkshopContent.NETHER_AUXILIARY.get() : WorkshopContent.END_AUXILIARY.get();
            h.getLevel().setBlockAndUpdate(auxiliary(center, i, rotation), block.defaultBlockState());
        }
        return (StabilizerBlockEntity) h.getLevel().getBlockEntity(center);
    }
    private static void ticks(StabilizerBlockEntity machine, int count) {
        for (int i = 0; i < count; i++) StabilizerBlockEntity.tick(machine.getLevel(), machine.getBlockPos(), machine.getBlockState(), machine);
    }
}
