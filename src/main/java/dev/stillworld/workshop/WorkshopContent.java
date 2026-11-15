package dev.stillworld.workshop;

import com.mojang.serialization.Codec;
import dev.stillworld.ModContent;
import dev.stillworld.Stillworld;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import java.util.function.Supplier;

/** Content for exploration and the two workstations; existing phase content keeps its IDs. */
public final class WorkshopContent {
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS=DeferredRegister.create(Registries.DATA_COMPONENT_TYPE,Stillworld.ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,Stillworld.ID);
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,Stillworld.ID);
    public static final Supplier<DataComponentType<Integer>> ANCHOR_VISITS=COMPONENTS.register("anchor_visits",()->DataComponentType.<Integer>builder().persistent(Codec.intRange(0,255)).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final DeferredBlock<Block> ORE=ModContent.BLOCKS.register("stillworld_ore",()->new Block(BlockBehaviour.Properties.of().strength(3,3).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> DEEPSLATE_ORE=ModContent.BLOCKS.register("deepslate_stillworld_ore",()->new Block(BlockBehaviour.Properties.of().strength(4.5f,3).sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops()));
    public static final DeferredItem<Item> UNSTABLE_MATERIAL=ModContent.ITEMS.registerSimpleItem("unstable_stillworld_material");
    public static final DeferredItem<Item> STABLE_MATERIAL=ModContent.ITEMS.registerSimpleItem("stable_stillworld_material");
    public static final DeferredItem<AnchorRecorderItem> ANCHOR_RECORDER=ModContent.ITEMS.register("anchor_recorder",AnchorRecorderItem::new);
    public static final DeferredBlock<StabilizerBlock> STABILIZER=ModContent.BLOCKS.register("distortion_stabilizer",StabilizerBlock::new);
    public static final DeferredBlock<AuxiliaryBlock> OVERWORLD_AUXILIARY=ModContent.BLOCKS.register("overworld_stabilizer_auxiliary",()->new AuxiliaryBlock(AuxiliaryBlock.Kind.OVERWORLD));
    public static final DeferredBlock<AuxiliaryBlock> NETHER_AUXILIARY=ModContent.BLOCKS.register("nether_stabilizer_auxiliary",()->new AuxiliaryBlock(AuxiliaryBlock.Kind.NETHER));
    public static final DeferredBlock<AuxiliaryBlock> END_AUXILIARY=ModContent.BLOCKS.register("end_stabilizer_auxiliary",()->new AuxiliaryBlock(AuxiliaryBlock.Kind.END));
    public static final Supplier<BlockEntityType<StabilizerBlockEntity>> STABILIZER_ENTITY=ENTITIES.register("distortion_stabilizer",()->BlockEntityType.Builder.of(StabilizerBlockEntity::new,STABILIZER.get()).build(null));
    public static final Supplier<BlockEntityType<AuxiliaryBlockEntity>> AUXILIARY_ENTITY=ENTITIES.register("stabilizer_auxiliary",()->BlockEntityType.Builder.of(AuxiliaryBlockEntity::new,OVERWORLD_AUXILIARY.get(),NETHER_AUXILIARY.get(),END_AUXILIARY.get()).build(null));
    public static final Supplier<BlockEntityType<TreatmentBlockEntity>> TREATMENT_ENTITY=ENTITIES.register("distortion_treatment_machine",()->BlockEntityType.Builder.of(TreatmentBlockEntity::new,ModContent.TREATMENT_MACHINE.get()).build(null));
    public static final Supplier<MenuType<StabilizerMenu>> STABILIZER_MENU=MENUS.register("distortion_stabilizer",()->new MenuType<>(StabilizerMenu::new,FeatureFlags.DEFAULT_FLAGS));

    public static void register(IEventBus bus) {
        ModContent.ITEMS.registerSimpleBlockItem(ORE);
        ModContent.ITEMS.registerSimpleBlockItem(DEEPSLATE_ORE);
        ModContent.ITEMS.registerSimpleBlockItem(STABILIZER);
        ModContent.ITEMS.registerSimpleBlockItem(OVERWORLD_AUXILIARY);
        ModContent.ITEMS.registerSimpleBlockItem(NETHER_AUXILIARY);
        ModContent.ITEMS.registerSimpleBlockItem(END_AUXILIARY);
        COMPONENTS.register(bus);ENTITIES.register(bus);MENUS.register(bus);
    }
    public static void creative(net.minecraft.world.item.CreativeModeTab.Output output) {
        output.accept(ORE);output.accept(DEEPSLATE_ORE);output.accept(UNSTABLE_MATERIAL);output.accept(STABLE_MATERIAL);
        output.accept(ANCHOR_RECORDER);output.accept(STABILIZER);output.accept(OVERWORLD_AUXILIARY);output.accept(NETHER_AUXILIARY);output.accept(END_AUXILIARY);
    }
    private WorkshopContent() {}
}
