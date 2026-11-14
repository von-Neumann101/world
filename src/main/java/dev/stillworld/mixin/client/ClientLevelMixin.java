package dev.stillworld.mixin.client;
import dev.stillworld.client.StillworldClient;
import dev.stillworld.client.RealityWorld;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = {"tickTime", "animateTick"}, at = @At("HEAD"), cancellable = true)
    private void stillworld$noEnvironmentTick(CallbackInfo ci) { if (StillworldClient.active() && !RealityWorld.isMirror((ClientLevel)(Object)this)) ci.cancel(); }
    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void stillworld$lockedEntity(Entity entity, CallbackInfo ci) {
        if (StillworldClient.locked(entity)) { StillworldClient.alignLocked(entity); ci.cancel(); }
    }
    @Inject(method = "tickPassenger", at = @At("HEAD"), cancellable = true)
    private void stillworld$lockedPassenger(Entity vehicle, Entity entity, CallbackInfo ci) {
        if (StillworldClient.locked(entity)) { StillworldClient.alignLocked(entity); ci.cancel(); }
    }
}
