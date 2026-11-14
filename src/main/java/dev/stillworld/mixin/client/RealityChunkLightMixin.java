package dev.stillworld.mixin.client;
import dev.stillworld.client.RealityWorld;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientChunkCache.class)
public abstract class RealityChunkLightMixin {
    @Shadow @Final private ClientLevel level;
    @Inject(method="onLightUpdate",at=@At("HEAD"),cancellable=true)
    private void stillworld$mirrorLight(LightLayer layer, SectionPos section, CallbackInfo ci) {
        if(RealityWorld.isMirror(level)) {
            if(RealityWorld.renderer()!=null)RealityWorld.renderer().setSectionDirty(section.x(),section.y(),section.z());
            ci.cancel();
        }
    }
}
