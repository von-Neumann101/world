package dev.stillworld.mixin.client;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ChestLidController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin({ChestBlockEntity.class,EnderChestBlockEntity.class})
public interface ChestLidHolderAccessor {
    @Accessor("chestLidController") ChestLidController stillworld$lid();
}
