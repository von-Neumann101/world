package dev.stillworld.mixin.client;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(SectionRenderDispatcher.CompiledSection.class)
public interface ConnectionSectionAccessor {
    @Accessor("transparencyState") MeshData.SortState stillworld$transparency();
}
