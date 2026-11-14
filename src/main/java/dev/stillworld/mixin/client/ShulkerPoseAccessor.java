package dev.stillworld.mixin.client;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ShulkerBoxBlockEntity.class)
public interface ShulkerPoseAccessor {
    @Accessor("progress") void stillworld$progress(float value);
    @Accessor("progressOld") void stillworld$previous(float value);
    @Accessor("animationStatus") void stillworld$status(ShulkerBoxBlockEntity.AnimationStatus value);
}
