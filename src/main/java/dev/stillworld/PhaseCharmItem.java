package dev.stillworld;

import dev.stillworld.core.Rank;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import java.util.List;

public final class PhaseCharmItem extends Item implements ICurioItem {
    private final Rank rank;
    public PhaseCharmItem(Rank rank) { super(new Properties().stacksTo(1)); this.rank = rank; }
    public Rank rank() { return rank; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags) {
        lines.add(Component.translatable("tooltip.stillworld.charm", rank.label()));
        if(rank.unlimitedPsyche())lines.add(Component.translatable("tooltip.stillworld.infinity"));
    }
}
