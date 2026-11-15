package dev.stillworld.workshop;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

public final class AnchorRecorderItem extends Item {
    public AnchorRecorderItem() { super(new Properties().stacksTo(1)); }

    /** Raw persisted/synchronized category mask, restricted to the eight defined bits. */
    public static int visited(ItemStack stack) {
        return stack.getItem() instanceof AnchorRecorderItem ? stack.getOrDefault(WorkshopContent.ANCHOR_VISITS.get(), 0) & 255 : 0;
    }
    public static boolean isFull(ItemStack stack) { return visited(stack) == 255; }

    public static void record(ItemStack stack, ResourceKey<Level> dimension, Holder<Biome> biome) {
        if (!(stack.getItem() instanceof AnchorRecorderItem)) return;
        int before = visited(stack), after = before;
        for (BiomeAnchor anchor : BiomeAnchor.values()) if (anchor.matches(dimension, biome)) after |= anchor.bit();
        if (after != before) stack.set(WorkshopContent.ANCHOR_VISITS.get(), after);
    }

    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && level.getGameTime() % 20 == 0 && !isFull(stack))
            record(stack, level.dimension(), level.getBiome(entity.blockPosition()));
    }

    // Sneaking normally bypasses block interaction. Handling item use as well makes
    // shift-right-click work regardless of the other hand's sneak-bypass behavior.
    @Override public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()
                && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof StabilizerBlockEntity machine) {
            if (!context.getLevel().isClientSide) machine.activate(context.getItemInHand(), player);
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        return super.useOn(context);
    }

    @Override public boolean isFoil(ItemStack stack) { return isFull(stack) || super.isFoil(stack); }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int mask = visited(stack);
        tooltip.add(Component.translatable("tooltip.stillworld.anchor_charge", Integer.bitCount(mask), 8)
                .withStyle(mask == 255 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
        for (BiomeAnchor anchor : BiomeAnchor.values()) tooltip.add(Component.translatable(
                (mask & anchor.bit()) != 0 ? "tooltip.stillworld.anchor_visited" : "tooltip.stillworld.anchor_missing",
                Component.translatable(anchor.translationKey())).withStyle((mask & anchor.bit()) != 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
    }
}
