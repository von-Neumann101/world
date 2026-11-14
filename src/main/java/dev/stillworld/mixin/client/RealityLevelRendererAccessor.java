package dev.stillworld.mixin.client;

import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanilla close releases post chains; a separately owned renderer also releases these meshes. */
@Mixin(LevelRenderer.class)
public interface RealityLevelRendererAccessor {
    @Accessor("starBuffer") VertexBuffer stillworld$starBuffer();
    @Accessor("skyBuffer") VertexBuffer stillworld$skyBuffer();
    @Accessor("darkBuffer") VertexBuffer stillworld$darkBuffer();
    @Accessor("cloudBuffer") VertexBuffer stillworld$cloudBuffer();

}
