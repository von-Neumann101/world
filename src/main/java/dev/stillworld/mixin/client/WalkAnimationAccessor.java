package dev.stillworld.mixin.client;
import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(WalkAnimationState.class)
public interface WalkAnimationAccessor {
    @Accessor("position") void stillworld$position(float value);
    @Accessor("speed") void stillworld$speed(float value);
    @Accessor("speedOld") void stillworld$speedOld(float value);
}
