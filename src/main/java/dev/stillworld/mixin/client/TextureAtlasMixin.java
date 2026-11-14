package dev.stillworld.mixin.client;
import dev.stillworld.client.StillworldClient;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin {
    @Inject(method = "cycleAnimationFrames", at = @At("HEAD"), cancellable = true)
    private void stillworld$freezeWorldTextures(CallbackInfo ci) {
        if (StillworldClient.active() && ((TextureAtlas)(Object)this).location().equals(InventoryMenu.BLOCK_ATLAS)) ci.cancel();
    }
}
