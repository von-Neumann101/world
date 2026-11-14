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

/** Runs the actual shipped GLSL on equally lit surfaces at known world distances. */
final class DistanceShaderProbe {
    static void check(Path output) throws Exception {
        var mc=Minecraft.getInstance();
        var target=new TextureTarget(64,64,true,Minecraft.ON_OSX);
        float[] distances={4,16,40,88,184};
        double[] brightness=new double[distances.length];
        var projection=new Matrix4f().perspective((float)Math.toRadians(70),1,.05f,512);
        try(var chain=new PostChain(mc.getTextureManager(),mc.getResourceManager(),target,Stillworld.id("shaders/post/monochrome.json"))) {
            chain.resize(64,64);
            for(var pass:((PostChainAccessor)(Object)chain).stillworld$passes()) {
                var effect=pass.getEffect();
                effect.safeGetUniform("InverseViewProjection").set(projection.invert(new Matrix4f()));
                effect.safeGetUniform("ScanProgress").set(1f);
            }
            for(int i=0;i<distances.length;i++) {
                target.setClearColor(.6f,.6f,.6f,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
                var point=projection.transform(new Vector4f(0,0,-distances[i],1));
                GlStateManager._clearDepth(point.z/point.w*.5+.5);
                GlStateManager._clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);
                RenderSystem.disableDepthTest();RenderSystem.disableBlend();chain.process(0);
                try(var image=Screenshot.takeScreenshot(target)) {
                    int c=image.getPixelRGBA(32,32);brightness[i]=((c&255)+((c>>8)&255)+((c>>16)&255))/765.0;
                }
            }
        } finally {
            GlStateManager._clearDepth(1);target.destroyBuffers();
            mc.getMainRenderTarget().bindWrite(true);RenderSystem.enableDepthTest();
        }
        Files.writeString(output.resolve("distance-shader-probe.txt"),"distances="+java.util.Arrays.toString(distances)+"\nbrightness="+java.util.Arrays.toString(brightness)+"\n");
        for(int i=1;i<brightness.length;i++) {
            if(brightness[i]>=brightness[i-1]-.035)throw new AssertionError("Equally lit distant surface did not get darker: "+java.util.Arrays.toString(brightness));
            // Each distance doubles (d + 8): logarithmic blackness must increase in equal steps.
            if(i>1&&Math.abs((brightness[i-1]-brightness[i])-(brightness[0]-brightness[1]))>.018)
                throw new AssertionError("Distance darkening was not logarithmic: "+java.util.Arrays.toString(brightness));
        }
        if(brightness[4]>=brightness[0]*.35)throw new AssertionError("Far surface remained too bright");
    }
}
