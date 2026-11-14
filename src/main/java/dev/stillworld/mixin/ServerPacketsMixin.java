package dev.stillworld.mixin;
import dev.stillworld.server.PhaseManager;
import dev.stillworld.server.RealitySync;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerPacketsMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"), order = 900)
    private void stillworld$reality(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
        if ((Object)this instanceof ServerGamePacketListenerImpl game) RealitySync.forward(game.player, packet);
    }
    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"), argsOnly = true)
    private Packet<?> stillworld$chunk(Packet<?> packet) {
        return (Object)this instanceof ServerGamePacketListenerImpl listener ? PhaseManager.replaceChunk(listener.player, packet) : packet;
    }
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"), cancellable = true)
    private void stillworld$updates(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
        if ((Object)this instanceof ServerGamePacketListenerImpl game && PhaseManager.suppress(game.player, packet)) ci.cancel();
    }
}
