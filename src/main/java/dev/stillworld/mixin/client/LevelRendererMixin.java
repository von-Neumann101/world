package dev.stillworld.mixin.client;
import dev.stillworld.client.StillworldClient;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = {"tick", "tickRain"}, at = @At("HEAD"), cancellable = true)
    private void stillworld$freezeCloudAndRainTime(CallbackInfo ci) { if (StillworldClient.active()) ci.cancel(); }
    @ModifyVariable(method = {"renderClouds", "renderSnowAndRain", "renderSky"}, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float stillworld$freezePartialTime(float partial) { return StillworldClient.active() ? 1 : partial; }
    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderGameTime(JF)V"), index = 1)
    private float stillworld$freezeShaderTime(float partial) { return StillworldClient.active() ? 1 : partial; }
}
