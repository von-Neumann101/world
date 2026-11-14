package dev.stillworld.mixin;
import dev.stillworld.server.PhaseManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PhaseInputMixin {
    @Shadow public ServerPlayer player;
    @Inject(method = {"handlePlayerAction", "handleUseItem", "handleUseItemOn", "handleInteract", "handleSetCreativeModeSlot"}, at = @At("HEAD"), cancellable = true)
    private void stillworld$ordinaryActions(CallbackInfo ci) { if (player.getServer().isSameThread() && PhaseManager.get(player) != null) ci.cancel(); }
    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void stillworld$inventory(CallbackInfo ci) {
        if (player.getServer().isSameThread() && PhaseManager.get(player) != null) { player.containerMenu.broadcastFullState(); ci.cancel(); }
    }
    @Inject(method = "noBlocksAround", at = @At("HEAD"), cancellable = true)
    private void stillworld$floating(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        var session = PhaseManager.get(player);
        if (session != null) cir.setReturnValue(session.snapshot().collisions(player, entity.getBoundingBox().inflate(0.0625).expandTowards(0, -0.55, 0)).isEmpty());
    }
}
