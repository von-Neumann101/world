package dev.stillworld.mixin;

import dev.stillworld.server.PhaseManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PhasePlayerTouchMixin {
    @Inject(method="touch",at=@At("HEAD"),cancellable=true)
    private void stillworld$noPickup(Entity target, CallbackInfo ci) {
        if(PhaseManager.isPhased((Player)(Object)this))ci.cancel();
    }
}
