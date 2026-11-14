package dev.stillworld.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import dev.stillworld.Stillworld;
import dev.stillworld.mixin.client.PostChainAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** World-only treatment, before hands/GUI and before the unfiltered skill/residual layer. */
public final class StillworldPostEffect {
    private static PostChain chain;
    private static Vec3 origin = Vec3.ZERO;
    private static long started;
    private static int width, height;
    private static boolean failed;
    private static float progress;

    static void registerReloads(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> release());
    }

    static void begin(Vec3 position) { clear(); origin=position; }
    public static boolean loaded() { return chain!=null; }
    public static boolean settled() { return loaded() && progress>=1; }
    static float elapsedSeconds() { return started==0?0:(System.nanoTime()-started)/1_000_000_000f; }

    static void render(RenderLevelStageEvent event) {
        var mc=Minecraft.getInstance();
        var target=mc.getMainRenderTarget();
        if(failed)return;
        if(chain==null) {
            try {
                chain=new PostChain(mc.getTextureManager(),mc.getResourceManager(),target,Stillworld.id("shaders/post/monochrome.json"));
                width=height=0;
            } catch(Exception exception) {
                failed=true;
                LogUtils.getLogger().error("Unable to load Stillworld vision; retry after resource reload",exception);
                return;
            }
        }
        if(width!=target.width||height!=target.height) {
            width=target.width;height=target.height;chain.resize(width,height);
        }
        long now=System.nanoTime();
        if(started==0)started=now;
        float elapsed=(now-started)/1_000_000_000f;
        progress=Math.min(1,elapsed/2.4f);
        float range=Math.max(64,mc.options.getEffectiveRenderDistance()*24f);
        Vec3 offset=event.getCamera().getPosition().subtract(origin);
        var inverse=new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix()).invert();
        for(var pass:((PostChainAccessor)(Object)chain).stillworld$passes()) {
            var effect=pass.getEffect();
            effect.safeGetUniform("InverseViewProjection").set(inverse);
            effect.safeGetUniform("CameraFromOrigin").set((float)offset.x,(float)offset.y,(float)offset.z);
            effect.safeGetUniform("ScanProgress").set(progress);
            effect.safeGetUniform("ScanRadius").set(progress*progress*range);
            effect.safeGetUniform("ScanRange").set(range);
            effect.safeGetUniform("Elapsed").set(elapsed);
            effect.safeGetUniform("DistortionStrength").set(mc.options.screenEffectScale().get().floatValue());
        }
        RenderSystem.disableBlend();RenderSystem.disableDepthTest();RenderSystem.resetTextureMatrix();
        chain.process(0);
        target.bindWrite(true);
    }

    private static void release() {
        if(chain!=null){chain.close();chain=null;}
        width=height=0;failed=false;
    }
    static void clear() { release();started=0;progress=0;origin=Vec3.ZERO; }
    private StillworldPostEffect() { }
}
