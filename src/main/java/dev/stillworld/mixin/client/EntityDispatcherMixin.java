package dev.stillworld.mixin.client;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.stillworld.client.StillworldRendering;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityDispatcherMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void stillworld$hide(E entity, double x, double y, double z, float yaw, float partial,
            PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (StillworldRendering.hideOriginal(entity)) ci.cancel();
    }
}
