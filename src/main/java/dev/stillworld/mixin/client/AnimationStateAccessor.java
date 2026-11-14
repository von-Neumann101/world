package dev.stillworld.mixin.client;
import net.minecraft.world.entity.AnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(AnimationState.class)
public interface AnimationStateAccessor {
    @Accessor("lastTime") long stillworld$lastTime();
    @Accessor("lastTime") void stillworld$lastTime(long value);
    @Accessor("accumulatedTime") long stillworld$accumulatedTime();
    @Accessor("accumulatedTime") void stillworld$accumulatedTime(long value);
}
