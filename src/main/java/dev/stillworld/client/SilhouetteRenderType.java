package dev.stillworld.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.stillworld.Stillworld;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/** Colors the actual textured geometry, including the transparent holes of generated item models. */
final class SilhouetteRenderType extends RenderType {
    private static ShaderInstance shader;
    private static final Map<ResourceLocation,RenderType> TYPES = new HashMap<>();

    static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(Stillworld.ID,"silhouette"),DefaultVertexFormat.NEW_ENTITY), loaded -> shader=loaded);
        } catch(IOException exception) { throw new UncheckedIOException("Cannot load the Stillworld silhouette shader",exception); }
    }

    static RenderType forTexture(ResourceLocation texture) {
        return TYPES.computeIfAbsent(texture,key -> create("stillworld_silhouette",DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,1536,true,true,CompositeState.builder()
                        .setShaderState(new ShaderStateShard(()->shader))
                        .setTextureState(new TextureStateShard(key,false,false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .createCompositeState(false)));
    }

    private SilhouetteRenderType() { super("stillworld_silhouette",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,1536,true,true,()->{},()->{}); }
}
