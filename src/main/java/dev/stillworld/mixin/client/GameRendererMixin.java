package dev.stillworld.mixin.client;
import dev.stillworld.client.StillworldClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "pick(F)V", at = @At("TAIL"))
    private void stillworld$snapshotTarget(float partial, CallbackInfo ci) {
        if (!StillworldClient.active()) return;
        var mc = Minecraft.getInstance();
        mc.hitResult = StillworldClient.pick(partial);
        mc.crosshairPickEntity = mc.hitResult instanceof EntityHitResult hit ? hit.getEntity() : null;
    }
}
