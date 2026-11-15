package dev.stillworld;

import dev.stillworld.core.Rank;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import top.theillusivec4.curios.api.CuriosApi;
import java.util.EnumMap;
import java.util.Map;

public final class ModContent {
    private static final DeferredRegister<net.neoforged.neoforge.attachment.AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES, Stillworld.ID);
    public static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<dev.stillworld.server.PsycheData>> PSYCHE = ATTACHMENTS.register("psyche", () -> net.neoforged.neoforge.attachment.AttachmentType.serializable(dev.stillworld.server.PsycheData::new).copyOnDeath().build());
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Stillworld.ID);
    public static final net.neoforged.neoforge.registries.DeferredBlock<dev.stillworld.block.MistBlock> BLACK_MIST = BLOCKS.register("black_mist", () -> new dev.stillworld.block.MistBlock(true));
    public static final net.neoforged.neoforge.registries.DeferredBlock<dev.stillworld.block.MistBlock> GRAY_MIST = BLOCKS.register("gray_mist", () -> new dev.stillworld.block.MistBlock(false));
    // Reserved workstation: treatment rules and recipes are deliberately not implemented yet.
    public static final net.neoforged.neoforge.registries.DeferredBlock<net.minecraft.world.level.block.Block> TREATMENT_MACHINE = BLOCKS.register("distortion_treatment_machine", () -> new net.minecraft.world.level.block.Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().strength(3.5f).requiresCorrectToolForDrops()));
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Stillworld.ID);
    public static final DeferredItem<BlockItem> TREATMENT_MACHINE_ITEM = ITEMS.registerSimpleBlockItem(TREATMENT_MACHINE);
    public static final Map<Rank, DeferredItem<PhaseCharmItem>> CHARMS = new EnumMap<>(Rank.class);
    public static final DeferredItem<SwordItem> BLADE = ITEMS.register("stillworld_blade", () -> new SwordItem(Tiers.NETHERITE,
            new Item.Properties().fireResistant().attributes(SwordItem.createAttributes(Tiers.NETHERITE, 3, -2.4f))));
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Stillworld.ID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB;
    static {
        for (Rank rank : Rank.values()) {
            CHARMS.put(rank, ITEMS.register("rank" + rank.label().replace('.', '_') + "_charm", () -> new PhaseCharmItem(rank)));
        }
        TAB = TABS.register("stillworld", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.stillworld"))
                .icon(() -> CHARMS.get(Rank.RANK1).get().getDefaultInstance())
                .displayItems((parameters, output) -> { CHARMS.values().forEach(output::accept); output.accept(BLADE); output.accept(TREATMENT_MACHINE_ITEM); }).build());
    }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); TABS.register(bus); ATTACHMENTS.register(bus); }
    public static Rank equippedRank(Player player) {
        var inventory = CuriosApi.getCuriosInventory(player);
        if (inventory.isEmpty()) return null;
        Rank result = null;
        var slots = inventory.get().getEquippedCurios();
        for (int i = 0; i < slots.getSlots(); i++) {
            if (slots.getStackInSlot(i).getItem() instanceof PhaseCharmItem charm
                    && (result == null || charm.rank().ordinal() > result.ordinal())) result = charm.rank();
        }
        return result;
    }
    private ModContent() { }
}
