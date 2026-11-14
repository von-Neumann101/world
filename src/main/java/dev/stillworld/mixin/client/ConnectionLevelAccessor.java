package dev.stillworld.mixin.client;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface ConnectionLevelAccessor {
    @Accessor("viewArea") ViewArea stillworld$viewArea();
    @Accessor("sectionRenderDispatcher") SectionRenderDispatcher stillworld$sectionDispatcher();
    @Accessor("globalBlockEntities") Set<BlockEntity> stillworld$globalBlockEntities();
}
