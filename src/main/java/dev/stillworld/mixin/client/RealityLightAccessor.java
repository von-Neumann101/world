package dev.stillworld.mixin.client;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Refresh lighting for a different visual level without advancing torch animation. */
@Mixin(LightTexture.class)
public interface RealityLightAccessor {
    @Accessor("updateLightTexture") void stillworld$dirty(boolean dirty);
    @Accessor("blockLightRedFlicker") float stillworld$flicker();
}
