package dev.stillworld.mixin.client;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Render-thread-only swaps, always restored by RealityOrbRenderer's finally block. */
@Mixin(Minecraft.class)
public interface RealityMinecraftAccessor {
    @Mutable @Accessor("levelRenderer") void stillworld$renderer(LevelRenderer renderer);
    @Mutable @Accessor("mainRenderTarget") void stillworld$target(RenderTarget target);
    @Mutable @Accessor("renderBuffers") void stillworld$buffers(RenderBuffers buffers);
}
