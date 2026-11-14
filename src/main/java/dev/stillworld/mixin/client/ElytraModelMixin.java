package dev.stillworld.mixin.client;
import dev.stillworld.client.StillworldClient;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Vanilla smooths wing angles during rendering, independently of entity ticks. */
@Mixin(ElytraModel.class)
public abstract class ElytraModelMixin {
    @Shadow @Final private ModelPart leftWing;
    @Shadow @Final private ModelPart rightWing;
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("HEAD"),cancellable=true)
    private void stillworld$keepFrozenWingAngles(LivingEntity entity,float limb,float speed,float age,float yaw,float pitch,CallbackInfo ci){
        if (!(entity instanceof AbstractClientPlayer player) || !StillworldClient.frozenVisual(entity)) return;
        leftWing.y=entity.isCrouching()?3:0;
        leftWing.xRot=player.elytraRotX;leftWing.yRot=player.elytraRotY;leftWing.zRot=player.elytraRotZ;
        rightWing.y=leftWing.y;rightWing.xRot=leftWing.xRot;rightWing.yRot=-leftWing.yRot;rightWing.zRot=-leftWing.zRot;
        ci.cancel();
    }
}
