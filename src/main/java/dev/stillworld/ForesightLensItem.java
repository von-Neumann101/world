package dev.stillworld;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public final class ForesightLensItem extends Item implements ICurioItem {
    public ForesightLensItem() { super(new Properties().stacksTo(1)); }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags) {
        lines.add(Component.translatable("tooltip.stillworld.foresight"));
        lines.add(Component.translatable("tooltip.stillworld.foresight_prediction"));
    }
}
