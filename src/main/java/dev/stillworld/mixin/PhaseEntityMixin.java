package dev.stillworld.mixin;
import dev.stillworld.server.PhaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Entity.class)
public abstract class PhaseEntityMixin {
    @Inject(method = "isSilent", at = @At("HEAD"), cancellable = true)
    private void stillworld$silent(CallbackInfoReturnable<Boolean> cir) { if (PhaseManager.isPhased((Entity)(Object)this)) cir.setReturnValue(true); }
    @Inject(method = "isIgnoringBlockTriggers", at = @At("HEAD"), cancellable = true)
    private void stillworld$triggers(CallbackInfoReturnable<Boolean> cir) { if (PhaseManager.isPhased((Entity)(Object)this)) cir.setReturnValue(true); }
    @Inject(method = "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void stillworld$vibration(Holder<GameEvent> event, Entity source, CallbackInfo ci) { if (PhaseManager.isPhased((Entity)(Object)this)) ci.cancel(); }
    @Inject(method = "checkInsideBlocks", at = @At("HEAD"), cancellable = true)
    private void stillworld$inside(CallbackInfo ci) { if (PhaseManager.isPhased((Entity)(Object)this)) ci.cancel(); }
    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void stillworld$push(Entity other, CallbackInfo ci) { if (PhaseManager.isPhased((Entity)(Object)this) || PhaseManager.isPhased(other)) ci.cancel(); }
    @Inject(method = "canCollideWith", at = @At("HEAD"), cancellable = true)
    private void stillworld$collide(Entity other, CallbackInfoReturnable<Boolean> cir) { if (PhaseManager.isPhased((Entity)(Object)this) || PhaseManager.isPhased(other)) cir.setReturnValue(false); }
    @Inject(method = "isPushedByFluid", at = @At("HEAD"), cancellable = true)
    private void stillworld$fluidPush(CallbackInfoReturnable<Boolean> cir) { if (PhaseManager.isPhased((Entity)(Object)this)) cir.setReturnValue(false); }
    @Inject(method = {"getBlockStateOn", "getBlockStateOnLegacy"}, at = @At("HEAD"), cancellable = true)
    private void stillworld$floor(CallbackInfoReturnable<BlockState> cir) {
        if ((Object)this instanceof ServerPlayer player && PhaseManager.get(player) != null)
            cir.setReturnValue(PhaseManager.get(player).snapshot().getBlockState(BlockPos.containing(player.getX(), player.getBoundingBox().minY - 0.2, player.getZ())));
    }
}
