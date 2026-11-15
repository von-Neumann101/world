package dev.stillworld.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.stillworld.Stillworld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import java.io.IOException;
import java.io.UncheckedIOException;

final class BladeRenderType extends RenderType {
    private static ShaderInstance shader;
    static final RenderType APERTURE=aperture(false),TRANSLUCENT_APERTURE=aperture(true);
    private static RenderType aperture(boolean translucent){return create("stillworld_blade_aperture"+(translucent?"_translucent":""),DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,1536,false,false,CompositeState.builder()
                    .setShaderState(new ShaderStateShard(()->{
                        var target=Minecraft.getInstance().getMainRenderTarget();
                        shader.safeGetUniform("ViewportSize").set((float)target.width,(float)target.height);
                        return shader;
                    }))
                    .setTextureState(new TextureStateShard(RealityBladeRenderer.APERTURE,false,false){
                        private int previous;
                        @Override public void setupRenderState(){
                            super.setupRenderState();previous=RenderSystem.getShaderTexture(1);
                            RenderSystem.setShaderTexture(1,RealityOrbRenderer.scene().getColorTextureId());
                        }
                        @Override public void clearRenderState(){RenderSystem.setShaderTexture(1,previous);super.clearRenderState();}
                    })
                    .setCullState(NO_CULL).setTransparencyState(translucent?TRANSLUCENT_TRANSPARENCY:NO_TRANSPARENCY)
                    .setWriteMaskState(translucent?COLOR_WRITE:COLOR_DEPTH_WRITE).createCompositeState(false));}
    static void registerShaders(RegisterShadersEvent event){
        try{event.registerShader(new ShaderInstance(event.getResourceProvider(),Stillworld.id("reality_blade"),
                DefaultVertexFormat.NEW_ENTITY),loaded->shader=loaded);}
        catch(IOException exception){throw new UncheckedIOException("Cannot load reality blade shader",exception);}
    }
    private BladeRenderType(){super("stillworld_blade",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,1536,false,false,()->{},()->{});}
}
