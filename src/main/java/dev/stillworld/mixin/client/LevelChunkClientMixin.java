package dev.stillworld.mixin.client;
import dev.stillworld.client.FrozenBlockEntities;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LevelChunk.class)
public abstract class LevelChunkClientMixin {
    @Inject(method="replaceWithPacketData",at=@At("HEAD"))
    private void stillworld$captureBeforeBlockEntitiesAreCleared(CallbackInfo ci){FrozenBlockEntities.beforeReplace((LevelChunk)(Object)this);}
    @Inject(method="replaceWithPacketData",at=@At("TAIL"))
    private void stillworld$restoreBlockEntityModelProgress(CallbackInfo ci){FrozenBlockEntities.afterReplace((LevelChunk)(Object)this);}
}
