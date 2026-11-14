package dev.stillworld.mixin;
import dev.stillworld.server.PhaseManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class PhaseLivingMixin {
    @Inject(method = {"canBeSeenByAnyone", "isPickable", "isPushable"}, at = @At("HEAD"), cancellable = true)
    private void stillworld$hidden(CallbackInfoReturnable<Boolean> cir) { if (PhaseManager.isPhased((Entity)(Object)this)) cir.setReturnValue(false); }
}
