package dev.stillworld.mixin.client;
import net.minecraft.world.level.block.entity.ChestLidController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ChestLidController.class)
public interface ChestLidAccessor {
    @Accessor("openness") float stillworld$open();
    @Accessor("openness") void stillworld$open(float value);
    @Accessor("oOpenness") float stillworld$previous();
    @Accessor("oOpenness") void stillworld$previous(float value);
    @Accessor("shouldBeOpen") boolean stillworld$shouldOpen();
    @Accessor("shouldBeOpen") void stillworld$shouldOpen(boolean value);
}
