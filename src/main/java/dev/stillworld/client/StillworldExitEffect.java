package dev.stillworld.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import dev.stillworld.Stillworld;
import dev.stillworld.mixin.client.PostChainAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import static org.lwjgl.opengl.GL30.*;

/** The gameplay session is already over; only its color/refraction treatment remains briefly. */
final class StillworldExitEffect {
    private static boolean active;
    private static long started;
    private static float startRadius,startOpacity,far,wave;
    private static RenderTarget live;
    private static boolean bladeSceneReady;
    private static PostChain compositor;

    static boolean active(){return active;}
    static RenderTarget scene(){return active&&bladeSceneReady?live:null;}
    static void invalidateBladeFrame(){bladeSceneReady=false;}
    static boolean begin() {
        clear();
        if(!StillworldClient.active()||!StillworldPostEffect.loaded())return false;
        startRadius=PsycheView.windowRadius();startOpacity=PsycheView.windowOpacity();
        far=PsycheView.windowFarBlend();wave=PsycheView.windowWaveTime();active=true;
        return true;
    }
    static void render(RenderLevelStageEvent event,RenderTarget sceneDepth) {
        var mc=Minecraft.getInstance();var main=mc.getMainRenderTarget();
        long now=System.nanoTime();if(started==0)started=now;
        float progress=Math.min(1,(now-started)/950_000_000f);
        try {
            if(progress>=1){finish();return;}
            resize(main);
            // Capture this frame, not an exit screenshot: moving and looking remain live.
            GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER,main.frameBufferId);
            GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER,live.frameBufferId);
            glBlitFramebuffer(0,0,main.width,main.height,0,0,live.width,live.height,GL_COLOR_BUFFER_BIT,GL_NEAREST);
            bladeSceneReady=true;
            main.bindWrite(true);
            StillworldPostEffect.renderExit(event);
            // The monochrome chain's final blit clears depth; refraction still needs real geometry.
            main.copyDepthFrom(sceneDepth);main.bindWrite(true);
            RealityBladeRenderer.renderWorldWindows();
            StillworldPostEffect.renderSpatial();
            float scale=(float)mc.getWindow().getGuiScale();
            float x=PsycheView.WINDOW_X*scale,y=main.height-PsycheView.WINDOW_Y*scale;
            float extent=(float)Math.hypot(Math.max(x,main.width-x),Math.max(y,main.height-y))*1.3f;
            float eased=progress*progress*(3-2*progress);
            var effect=((PostChainAccessor)(Object)compositor).stillworld$passes().getFirst().getEffect();
            effect.safeGetUniform("HudMode").set(2f);
            effect.safeGetUniform("HudCenter").set(x,y);
            effect.safeGetUniform("HudRadius").set(startRadius*scale+(extent-startRadius*scale)*eased);
            effect.safeGetUniform("HudOpacity").set(startOpacity+(1-startOpacity)*Math.min(1,progress/.15f));
            effect.safeGetUniform("HudFar").set(far);
            effect.safeGetUniform("HudWaveTime").set(wave+progress*.95f*(.4f+.15f*far));
            effect.safeGetUniform("ExitProgress").set(progress);
            RenderSystem.disableBlend();RenderSystem.disableDepthTest();RenderSystem.resetTextureMatrix();
            compositor.process(0);
        } catch(Exception exception) {
            LogUtils.getLogger().error("Unable to render Stillworld exit transition",exception);
            finish();
        } finally {
            main.bindWrite(true);RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
        }
    }
    private static void resize(RenderTarget main) throws Exception {
        if(live!=null&&live.width==main.width&&live.height==main.height)return;
        release();
        live=new TextureTarget(main.width,main.height,false,Minecraft.ON_OSX);
        var mc=Minecraft.getInstance();
        compositor=new PostChain(mc.getTextureManager(),mc.getResourceManager(),main,Stillworld.id("shaders/post/reality_orb.json"));
        ((PostChainAccessor)(Object)compositor).stillworld$passes().getFirst()
                .addAuxAsset("RealitySampler",live::getColorTextureId,live.width,live.height);
        compositor.resize(main.width,main.height);
    }
    private static void release(){bladeSceneReady=false;if(compositor!=null){compositor.close();compositor=null;}if(live!=null){live.destroyBuffers();live=null;}}
    static void clear(){release();active=false;started=0;}
    private static void finish(){clear();StillworldPostEffect.clear();StillworldRendering.clear();}
    private StillworldExitEffect(){}
}
