package dev.stillworld.mixin.client;
import dev.stillworld.client.StillworldClient;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityDispatcherMixin {
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float stillworld$frozenBlockEntityInterpolation(float partial) { return StillworldClient.active() ? 1 : partial; }
}
