package dev.stillworld.mixin.client;
import dev.stillworld.client.StillworldClient;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Level.class)
public abstract class ClientWorldMixin {
    @Shadow @Final public boolean isClientSide;
    @Inject(method = "tickBlockEntities", at = @At("HEAD"), cancellable = true)
    private void stillworld$noBlockEntityTick(CallbackInfo ci) {
        if (isClientSide && StillworldClient.active()) ci.cancel();
    }
    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void stillworld$rain(float partial, CallbackInfoReturnable<Float> cir) {
        if (isClientSide && StillworldClient.active()) cir.setReturnValue(StillworldClient.frozenRain());
    }
    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void stillworld$thunder(float partial, CallbackInfoReturnable<Float> cir) {
        if (isClientSide && StillworldClient.active()) cir.setReturnValue(StillworldClient.frozenThunder());
    }
    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    private void stillworld$day(CallbackInfoReturnable<Long> cir) {
        if (isClientSide && StillworldClient.active()) cir.setReturnValue(StillworldClient.frozenDay());
    }
    @Inject(method = "getGameTime", at = @At("HEAD"), cancellable = true)
    private void stillworld$time(CallbackInfoReturnable<Long> cir) {
        if (isClientSide && StillworldClient.active()) cir.setReturnValue(StillworldClient.frozenTime());
    }
}
