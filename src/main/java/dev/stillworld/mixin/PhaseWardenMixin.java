package dev.stillworld.mixin;

import dev.stillworld.server.PhaseManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Warden.class)
public abstract class PhaseWardenMixin {
    @Inject(method="canTargetEntity",at=@At("HEAD"),cancellable=true)
    private void stillworld$noScent(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if(target!=null&&PhaseManager.isPhased(target))cir.setReturnValue(false);
    }
}
