package dev.stillworld.mixin;

import dev.stillworld.workshop.TreatmentBlock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class TreatmentCrouchMixin {
    @Inject(method="setShiftKeyDown",at=@At("HEAD"),cancellable=true)
    private void stillworld$treatmentPosture(boolean down,CallbackInfo ci) {
        if(down&&(Object)this instanceof Player player&&TreatmentBlock.isTreating(player))ci.cancel();
    }
}
