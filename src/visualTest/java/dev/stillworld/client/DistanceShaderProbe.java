package dev.stillworld.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.stillworld.Stillworld;
import dev.stillworld.mixin.client.PostChainAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.PostChain;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.nio.file.Files;
import java.nio.file.Path;

/** Runs the shipped GLSL on dark/light surfaces to check atmospheric depth and sky headroom. */
final class DistanceShaderProbe {
    static void check(Path output) throws Exception {
        var mc=Minecraft.getInstance();
        var target=new TextureTarget(64,64,true,Minecraft.ON_OSX);
        float[] distances={4,16,40,88,184,512};
        float[] tones={.08f,.8f};
        double[][] brightness=new double[tones.length][distances.length];
        double[] sky=new double[3];
        double rim;
        var projection=new Matrix4f().perspective((float)Math.toRadians(70),1,.05f,2048);
        try(var chain=new PostChain(mc.getTextureManager(),mc.getResourceManager(),target,Stillworld.id("shaders/post/monochrome.json"))) {
            chain.resize(64,64);
            for(var pass:((PostChainAccessor)(Object)chain).stillworld$passes()) {
                var effect=pass.getEffect();
                effect.safeGetUniform("InverseViewProjection").set(projection.invert(new Matrix4f()));
                effect.safeGetUniform("ScanProgress").set(1f);
            }
            for(int tone=0;tone<tones.length;tone++)for(int i=0;i<distances.length;i++) {
                var point=projection.transform(new Vector4f(0,0,-distances[i],1));
                brightness[tone][i]=sample(target,chain,tones[tone],point.z/point.w*.5+.5);
            }
            sky[0]=sample(target,chain,0,1);sky[1]=sample(target,chain,1,1);
            // Empty lower sky can retain framebuffer alpha zero; the completed scene must still be visible.
            sky[2]=sample(target,chain,0,1,0);
            target.setClearColor(.2f,.2f,.2f,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            var near=projection.transform(new Vector4f(0,0,-4,1));
            RenderSystem.enableScissor(0,0,32,64);
            try {
                GlStateManager._clearDepth(near.z/near.w*.5+.5);
                GlStateManager._clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);
            } finally {RenderSystem.disableScissor();}
            RenderSystem.disableDepthTest();RenderSystem.disableBlend();chain.process(0);
            try(var image=Screenshot.takeScreenshot(target)) {
                rim=brightness(image.getPixelRGBA(31,32))-brightness(image.getPixelRGBA(16,32));
            }
        } finally {
            GlStateManager._clearDepth(1);target.destroyBuffers();
            mc.getMainRenderTarget().bindWrite(true);RenderSystem.enableDepthTest();
        }
        Files.writeString(output.resolve("distance-shader-probe.txt"),"distances="+java.util.Arrays.toString(distances)
                +"\ndark="+java.util.Arrays.toString(brightness[0])+"\nlight="+java.util.Arrays.toString(brightness[1])+"\nsky="+java.util.Arrays.toString(sky)+"\nrim="+rim+"\n");
        double nearContrast=brightness[1][0]-brightness[0][0];
        if(nearContrast<.30)throw new AssertionError("Near textures lost their light/shadow contrast");
        for(int i=1;i<distances.length;i++) {
            double contrast=brightness[1][i]-brightness[0][i];
            double previous=brightness[1][i-1]-brightness[0][i-1];
            if(brightness[0][i]<=brightness[0][i-1]+.01)throw new AssertionError("Distant shadows did not lift into silver-gray haze");
            if(contrast>=previous-.01)throw new AssertionError("Atmospheric contrast did not decrease with distance");
            if(contrast<nearContrast*.18)throw new AssertionError("Distant layers lost all light/shadow separation");
        }
        if(brightness[0][4]<.42||brightness[1][5]>.78)throw new AssertionError("Distant tones became oppressive black or featureless white");
        if(brightness[1][4]-brightness[0][4]>nearContrast*.6)throw new AssertionError("Distant textures remained too high-contrast");
        if(sky[0]<.55||sky[1]>.88||sky[1]-sky[0]<.04)throw new AssertionError("Sky needs a pale atmosphere with visible cloud layering");
        if(Math.abs(sky[2]-sky[0])>.01)throw new AssertionError("Transparent lower sky became a black horizon in the final composite");
        if(rim<.06||rim>.24)throw new AssertionError("Structural rim must be present but secondary to the surface: "+rim);
    }

    private static double sample(TextureTarget target,PostChain chain,float tone,double depth) {
        return sample(target,chain,tone,depth,1);
    }

    private static double sample(TextureTarget target,PostChain chain,float tone,double depth,float alpha) {
        target.setClearColor(tone,tone,tone,alpha);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
        GlStateManager._clearDepth(depth);
        GlStateManager._clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);
        RenderSystem.disableDepthTest();RenderSystem.disableBlend();chain.process(0);
        try(var image=Screenshot.takeScreenshot(target)) {
            return brightness(image.getPixelRGBA(32,32));
        }
    }

    private static double brightness(int rgba) {return ((rgba&255)+((rgba>>8)&255)+((rgba>>16)&255))/765.0;}
}
