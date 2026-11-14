package dev.stillworld.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import dev.stillworld.Stillworld;
import dev.stillworld.mixin.client.PostChainAccessor;
import dev.stillworld.mixin.client.RealityMinecraftAccessor;
import dev.stillworld.mixin.client.RealityLightAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** One non-recursive, visual-only live view, composited through the entry sphere. */
public final class RealityOrbRenderer {
    private static Vec3 center;
    private static RenderTarget reality, sceneDepth;
    private static PostChain compositor;
    private static boolean rendering, failed, rebuild=true;
    private static GraphicsStatus graphics;
    private static int renderedFrames;
    private static int viewDistance;

    public static boolean rendering(){return rendering;}
    static Vec3 center(){return center;}
    static int renderedFrames(){return renderedFrames;}
    static RenderTarget scene(){return reality;}
    static void begin(Vec3 entry){clear();center=entry.add(0,1,0);}
    static void registerReloads(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener)resources->{releaseTargets();failed=false;rebuild=true;});
    }

    static void render(RenderLevelStageEvent event) {
        var mc=Minecraft.getInstance();
        if(rendering||failed||center==null||RealityWorld.level()==null||!StillworldPostEffect.loaded())return;
        Vec3 eye=event.getCamera().getPosition();
        double distance=eye.distanceTo(center);
        // Inside the shell there is no outward-facing surface to display. A wide fade
        // avoids covering the whole view when entering or walking through the anchor.
        if(distance<=1.05||!event.getFrustum().isVisible(new AABB(center,center).inflate(1.03)))return;
        float opacity=(float)Math.clamp((distance-1.05)/.45,0,1);
        var main=mc.getMainRenderTarget();
        boolean depthSaved=false;
        try {
            resize(main);
            sceneDepth.copyDepthFrom(main);
            depthSaved=true;
            drawReality(event);
            main.bindWrite(true);
            var vp=new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
            var effect=((PostChainAccessor)(Object)compositor).stillworld$passes().getFirst().getEffect();
            var relative=center.subtract(eye);
            effect.safeGetUniform("InverseViewProjection").set(vp.invert(new Matrix4f()));
            effect.safeGetUniform("ViewProjection").set(vp);
            effect.safeGetUniform("SphereCenter").set((float)relative.x,(float)relative.y,(float)relative.z);
            effect.safeGetUniform("SphereRadius").set(1f);
            effect.safeGetUniform("Opacity").set(opacity);
            RenderSystem.disableBlend();RenderSystem.disableDepthTest();RenderSystem.resetTextureMatrix();
            compositor.process(0);
            renderedFrames++;
        } catch(Exception exception) {
            failed=true;
            LogUtils.getLogger().error("Unable to render Stillworld reality orb; retry after resource reload",exception);
        } finally {
            // The final post-chain blit clears depth. The following radial refraction
            // still needs the frozen world's depth, not the mirror or an empty sky.
            if(depthSaved)main.copyDepthFrom(sceneDepth);
            main.bindWrite(true);RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
        }
    }

    private static void drawReality(RenderLevelStageEvent event) {
        var mc=Minecraft.getInstance();var access=(RealityMinecraftAccessor)mc;
        var originalLevel=mc.level;var originalRenderer=mc.levelRenderer;
        var originalBuffers=mc.renderBuffers();var originalTarget=mc.getMainRenderTarget();
        var originalProjection=new Matrix4f(RenderSystem.getProjectionMatrix());
        float fogStart=RenderSystem.getShaderFogStart(),fogEnd=RenderSystem.getShaderFogEnd();
        var fogShape=RenderSystem.getShaderFogShape();var fogColor=RenderSystem.getShaderFogColor().clone();
        var stack=RenderSystem.getModelViewStack();stack.pushMatrix();
        var renderer=RealityWorld.renderer();
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var camera=new LiveCamera(event.getCamera(),partial);
        rendering=true;
        mc.getProfiler().push("stillworld_reality");
        try {
            mc.level=RealityWorld.level();access.stillworld$renderer(renderer);
            access.stillworld$buffers(RealityWorld.buffers());access.stillworld$target(reality);
            if(rebuild||graphics!=mc.options.graphicsMode().get()||viewDistance!=mc.options.getEffectiveRenderDistance()) {
                // Vanilla post chains capture their main target at creation, including
                // Fabulous. Rebuild only while our offscreen target is the active one.
                renderer.onResourceManagerReload(mc.getResourceManager());renderer.allChanged();
                renderer.resize(reality.width,reality.height);
                graphics=mc.options.graphicsMode().get();viewDistance=mc.options.getEffectiveRenderDistance();rebuild=false;
            }
            reality.bindWrite(true);RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
            stack.identity();RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(event.getProjectionMatrix(),VertexSorting.DISTANCE_TO_ORIGIN);
            ((RealityLightAccessor)mc.gameRenderer.lightTexture()).stillworld$dirty(true);
            mc.gameRenderer.lightTexture().updateLightTexture(partial);
            renderer.prepareCullFrustum(camera.getPosition(),event.getModelViewMatrix(),event.getProjectionMatrix());
            renderer.renderLevel(event.getPartialTick(),false,camera,mc.gameRenderer,mc.gameRenderer.lightTexture(),
                    event.getModelViewMatrix(),event.getProjectionMatrix());
        } finally {
            mc.level=originalLevel;access.stillworld$renderer(originalRenderer);
            access.stillworld$buffers(originalBuffers);access.stillworld$target(originalTarget);
            rendering=false;
            mc.getEntityRenderDispatcher().prepare(originalLevel,event.getCamera(),mc.crosshairPickEntity);
            mc.getBlockEntityRenderDispatcher().prepare(originalLevel,event.getCamera(),mc.hitResult);
            ((RealityLightAccessor)mc.gameRenderer.lightTexture()).stillworld$dirty(true);
            mc.gameRenderer.lightTexture().updateLightTexture(partial);
            mc.gameRenderer.lightTexture().turnOffLightLayer();
            RenderSystem.setShaderGameTime(originalLevel.getGameTime(),1);
            stack.popMatrix();RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(originalProjection,VertexSorting.DISTANCE_TO_ORIGIN);
            FogRenderer.setupColor(event.getCamera(),partial,originalLevel,mc.options.getEffectiveRenderDistance(),mc.gameRenderer.getDarkenWorldAmount(partial));
            RenderSystem.setShaderFogStart(fogStart);RenderSystem.setShaderFogEnd(fogEnd);RenderSystem.setShaderFogShape(fogShape);
            RenderSystem.setShaderFogColor(fogColor[0],fogColor[1],fogColor[2],fogColor[3]);
            RenderSystem.setShaderColor(1,1,1,1);RenderSystem.disableBlend();RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);RenderSystem.depthMask(true);
            originalTarget.bindWrite(true);mc.getProfiler().pop();
        }
    }

    private static void resize(RenderTarget main) throws Exception {
        if(sceneDepth!=null&&sceneDepth.width==main.width&&sceneDepth.height==main.height)return;
        releaseTargets();
        // A small window into reality does not need a second full-resolution frame.
        reality=new TextureTarget(Math.max(1,main.width/2),Math.max(1,main.height/2),true,Minecraft.ON_OSX);
        sceneDepth=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);
        var mc=Minecraft.getInstance();
        compositor=new PostChain(mc.getTextureManager(),mc.getResourceManager(),main,Stillworld.id("shaders/post/reality_orb.json"));
        ((PostChainAccessor)(Object)compositor).stillworld$passes().getFirst()
                .addAuxAsset("RealitySampler",reality::getColorTextureId,reality.width,reality.height);
        compositor.resize(main.width,main.height);rebuild=true;
    }
    private static void releaseTargets() {
        if(compositor!=null){compositor.close();compositor=null;}
        if(reality!=null){reality.destroyBuffers();reality=null;}
        if(sceneDepth!=null){sceneDepth.destroyBuffers();sceneDepth=null;}
    }
    static void clear(){releaseTargets();center=null;rendering=false;failed=false;rebuild=true;graphics=null;renderedFrames=0;}
    private static final class LiveCamera extends Camera {
        LiveCamera(Camera original,float partial) {
            setup(RealityWorld.level(),original.getEntity(),original.isDetached(),false,partial);
            setPosition(original.getPosition());setRotation(original.getYRot(),original.getXRot());
        }
    }
    private RealityOrbRenderer() { }
}
