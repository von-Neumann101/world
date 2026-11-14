package dev.stillworld.mixin.client;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.stillworld.client.StillworldRendering;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** The base renderer draws leashes and nameplates, both omitted from custom visual passes. */
@Mixin(EntityRenderer.class)
public abstract class EntityExtrasMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <T extends Entity> void stillworld$noExtras(T entity, float yaw, float partial, PoseStack pose,
            MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (StillworldRendering.visualPass()) ci.cancel();
    }
}
