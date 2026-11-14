package dev.stillworld.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.logging.LogUtils;
import dev.stillworld.Stillworld;
import dev.stillworld.mixin.client.ConnectionLevelAccessor;
import dev.stillworld.mixin.client.ConnectionSectionAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Non-recursive extra scene view. Never changes player/world coordinates or the real camera. */
final class ConnectionRenderer {
    private static ShaderInstance compositor;
    private static RenderTarget source,remote,remoteDepth;
    private static PostChain colorChain;
    private static SpatialConnection connection;
    private static long anchoredAt;
    private static boolean failed;
    private static int renderedViews;
    private static ViewBuffers buffers;
    private static ByteBufferBuilder indices;
    private static int elementBuffer;

    static void registerShaders(RegisterShadersEvent event) {
        try {event.registerShader(new ShaderInstance(event.getResourceProvider(),Stillworld.id("connection"),
                DefaultVertexFormat.POSITION),shader->compositor=shader);}
        catch(IOException exception){throw new UncheckedIOException(exception);}
    }
    static int renderedViews(){return renderedViews;}
    static void render(RenderLevelStageEvent event) {
        var mc=Minecraft.getInstance();
        if(failed || compositor==null || !StillworldPostEffect.loaded() || mc.level==null || mc.player==null)return;
        var camera=event.getCamera();var position=camera.getPosition();
        if(connection==null || position.distanceTo(connection.anchor())>80) {
            connection=new SpatialConnection(position,Vec3.directionFromRotation(0,camera.getYRot()));
            anchoredAt=System.nanoTime();
        }
        double reach=Math.max(mc.player.blockInteractionRange(),mc.player.entityInteractionRange());
        float strength=connection.strength(position,reach)*mc.options.screenEffectScale().get().floatValue()
                *SpatialConnection.smooth(.2,1,StillworldPostEffect.progress())
                *SpatialConnection.smooth(0,.6,(System.nanoTime()-anchoredAt)/1_000_000_000.0);
        if(strength<.001 || !event.getFrustum().isVisible(connection.bounds()))return;
        var view=connection.remoteView(event.getModelViewMatrix());
        var remotePosition=connection.map(position);
        var projection=connection.clippedProjection(event.getProjectionMatrix(),view,remotePosition);
        if(projection==null)return;
        var frustum=new Frustum(view,projection);frustum.prepare(remotePosition.x,remotePosition.y,remotePosition.z);
        var sections=sections(frustum,remotePosition);
        if(sections==null)return; // Wait for missing destination meshes instead of showing holes.

        var main=mc.getMainRenderTarget();
        var previousProjection=new Matrix4f(RenderSystem.getProjectionMatrix());
        float fogStart=RenderSystem.getShaderFogStart(),fogEnd=RenderSystem.getShaderFogEnd();
        float[] fogColor=RenderSystem.getShaderFogColor().clone();var fogShape=RenderSystem.getShaderFogShape();
        var stack=RenderSystem.getModelViewStack();stack.pushMatrix();
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var otherCamera=new ViewCamera(camera,remotePosition,partial);
        try {
            resize(main.width,main.height);
            copy(main,source);
            drawScene(otherCamera,view,projection,event.getProjectionMatrix(),frustum,sections,partial);
            remoteDepth.copyDepthFrom(remote);
            StillworldPostEffect.shade(colorChain,remote,view,projection,remotePosition);
            remote.copyDepthFrom(remoteDepth);remote.bindWrite(true);
            stack.identity().mul(view);RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection,VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
            mc.gameRenderer.lightTexture().turnOnLightLayer();
            StillworldRendering.drawEffects(otherCamera,frustum,partial,buffers);
            main.bindWrite(true);
            composite(event,position,strength);
            renderedViews++;
        } catch(Exception exception) {
            failed=true;
            LogUtils.getLogger().error("Stillworld connection view failed; ordinary snapshot view remains active until reload",exception);
        } finally {
            if(buffers!=null)buffers.endBatch();
            mc.getEntityRenderDispatcher().prepare(mc.level,camera,mc.crosshairPickEntity);
            mc.getBlockEntityRenderDispatcher().prepare(mc.level,camera,mc.hitResult);
            mc.gameRenderer.lightTexture().turnOffLightLayer();
            stack.popMatrix();RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection,VertexSorting.DISTANCE_TO_ORIGIN);
            FogRenderer.setupColor(camera,partial,mc.level,mc.options.getEffectiveRenderDistance(),mc.gameRenderer.getDarkenWorldAmount(partial));
            RenderSystem.setShaderFogStart(fogStart);RenderSystem.setShaderFogEnd(fogEnd);RenderSystem.setShaderFogShape(fogShape);
            RenderSystem.setShaderFogColor(fogColor[0],fogColor[1],fogColor[2],fogColor[3]);
            RenderSystem.setShaderColor(1,1,1,1);RenderSystem.depthMask(true);RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL30.GL_LEQUAL);RenderSystem.disableBlend();main.bindWrite(true);
        }
    }

    private static List<SectionRenderDispatcher.RenderSection> sections(Frustum frustum,Vec3 camera) {
        var mc=Minecraft.getInstance();var access=(ConnectionLevelAccessor)mc.levelRenderer;
        var area=access.stillworld$viewArea();if(area==null)return null;
        var result=new ArrayList<SectionRenderDispatcher.RenderSection>();
        var cache=new RenderRegionCache();int scheduled=0;boolean ready=true;
        double range=Math.max(32,mc.options.getEffectiveRenderDistance()*16-16);
        for(var section:area.sections) {
            var origin=section.getOrigin();
            if(Math.abs(origin.getX()+8-camera.x)>range || Math.abs(origin.getZ()+8-camera.z)>range
                    || !frustum.isVisible(section.getBoundingBox()) || !section.hasAllNeighbors())continue;
            if(!mc.level.getLightEngine().lightOnInSection(SectionPos.of(origin)))continue;
            if(section.isDirty() && scheduled<8) {
                section.rebuildSectionAsync(access.stillworld$sectionDispatcher(),cache);section.setNotDirty();scheduled++;
            }
            if(section.getCompiled()==SectionRenderDispatcher.CompiledSection.UNCOMPILED)ready=false;
            result.add(section);
        }
        result.sort(Comparator.comparingDouble(section->section.getBoundingBox().getCenter().distanceToSqr(camera)));
        return ready&&!result.isEmpty()?result:null;
    }

    private static void drawScene(Camera camera,Matrix4f view,Matrix4f clipped,Matrix4f skyProjection,Frustum frustum,
                                  List<SectionRenderDispatcher.RenderSection> sections,float partial) {
        var mc=Minecraft.getInstance();var stack=RenderSystem.getModelViewStack();
        remote.setClearColor(.7f,.72f,.74f,1);remote.clear(Minecraft.ON_OSX);remote.bindWrite(true);
        RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.disableBlend();
        mc.getEntityRenderDispatcher().prepare(mc.level,camera,mc.crosshairPickEntity);
        mc.getBlockEntityRenderDispatcher().prepare(mc.level,camera,mc.hitResult);
        FogRenderer.setupColor(camera,partial,mc.level,mc.options.getEffectiveRenderDistance(),mc.gameRenderer.getDarkenWorldAmount(partial));
        stack.identity();RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(skyProjection,VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.setShader(GameRenderer::getPositionShader);
        mc.levelRenderer.renderSky(view,skyProjection,1,camera,false,
                ()->FogRenderer.setupFog(camera,FogRenderer.FogMode.FOG_SKY,mc.gameRenderer.getRenderDistance(),false,partial));
        FogRenderer.setupFog(camera,FogRenderer.FogMode.FOG_TERRAIN,Math.max(32,mc.gameRenderer.getRenderDistance()),false,partial);
        RenderSystem.setProjectionMatrix(clipped,VertexSorting.DISTANCE_TO_ORIGIN);
        stack.identity().mul(view);RenderSystem.applyModelViewMatrix();
        RenderSystem.setShaderColor(1,1,1,1);mc.gameRenderer.lightTexture().turnOnLightLayer();
        terrain(RenderType.solid(),sections,camera.getPosition(),view,clipped);
        terrain(RenderType.cutoutMipped(),sections,camera.getPosition(),view,clipped);
        terrain(RenderType.cutout(),sections,camera.getPosition(),view,clipped);
        var pose=new PoseStack();
        for(var section:sections)for(var entity:section.getCompiled().getRenderableBlockEntities()) {
            var pos=entity.getBlockPos();pose.pushPose();
            pose.translate(pos.getX()-camera.getPosition().x,pos.getY()-camera.getPosition().y,pos.getZ()-camera.getPosition().z);
            mc.getBlockEntityRenderDispatcher().render(entity,1,pose,buffers);pose.popPose();
        }
        var global=((ConnectionLevelAccessor)mc.levelRenderer).stillworld$globalBlockEntities();
        synchronized(global) {
            for(var entity:global) {
                if(!net.neoforged.neoforge.client.ClientHooks.isBlockEntityRendererVisible(mc.getBlockEntityRenderDispatcher(),entity,frustum))continue;
                var pos=entity.getBlockPos();pose.pushPose();
                pose.translate(pos.getX()-camera.getPosition().x,pos.getY()-camera.getPosition().y,pos.getZ()-camera.getPosition().z);
                mc.getBlockEntityRenderDispatcher().render(entity,1,pose,buffers);pose.popPose();
            }
        }
        StillworldRendering.drawFrozen(camera,frustum,buffers);buffers.endBatch();
        terrain(RenderType.translucent(),sections,camera.getPosition(),view,clipped);
        terrain(RenderType.tripwire(),sections,camera.getPosition(),view,clipped);
        remote.bindWrite(true);
    }

    private static void terrain(RenderType layer,List<SectionRenderDispatcher.RenderSection> sections,Vec3 camera,Matrix4f view,Matrix4f projection) {
        layer.setupRenderState();remote.bindWrite(true); // Includes Fabulous layers whose original output is a separate target.
        var shader=RenderSystem.getShader();
        try {
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS,view,projection,Minecraft.getInstance().getWindow());shader.apply();
            boolean transparent=layer==RenderType.translucent() || layer==RenderType.tripwire();
            for(int n=0;n<sections.size();n++) {
                var section=sections.get(transparent?sections.size()-n-1:n);
                if(section.getCompiled().isEmpty(layer))continue;
                var origin=section.getOrigin();
                if(shader.CHUNK_OFFSET!=null) {
                    shader.CHUNK_OFFSET.set((float)(origin.getX()-camera.x),(float)(origin.getY()-camera.y),(float)(origin.getZ()-camera.z));
                    shader.CHUNK_OFFSET.upload();
                }
                var buffer=section.getBuffer(layer);buffer.bind();
                var sorting=layer==RenderType.translucent()?((ConnectionSectionAccessor)section.getCompiled()).stillworld$transparency():null;
                if(sorting==null)buffer.draw();
                else {
                    // The primary view's VAO/index order belongs to vanilla. Restore it after this draw.
                    int originalElements=GL30.glGetInteger(GL30.GL_ELEMENT_ARRAY_BUFFER_BINDING);
                    try(var sorted=sorting.buildSortedIndexBuffer(indices,VertexSorting.byDistance(
                            (float)(camera.x-origin.getX()),(float)(camera.y-origin.getY()),(float)(camera.z-origin.getZ())))) {
                        if(sorted!=null) {
                            GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER,elementBuffer);
                            GL30.glBufferData(GL30.GL_ELEMENT_ARRAY_BUFFER,sorted.byteBuffer(),GL30.GL_STREAM_DRAW);
                        }
                        buffer.draw();
                    } finally {GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER,originalElements);indices.clear();}
                }
            }
        } finally {
            if(shader.CHUNK_OFFSET!=null)shader.CHUNK_OFFSET.set(0f,0f,0f);
            shader.clear();VertexBuffer.unbind();layer.clearRenderState();remote.bindWrite(true);
        }
    }

    /** Fixed storage keeps simultaneous foil/base consumers alive. Flush base geometry before EQUAL-depth foil. */
    private static final class ViewBuffers extends MultiBufferSource.BufferSource implements AutoCloseable {
        private final Map<RenderType,ViewLayer> layers=new IdentityHashMap<>();
        ViewBuffers(){super(new ByteBufferBuilder(1536),new LinkedHashMap<>());}
        @Override public VertexConsumer getBuffer(RenderType original) {
            var layer=layers.computeIfAbsent(original,type->{
                var created=new ViewLayer(type);fixedBuffers.put(created,new ByteBufferBuilder(type.bufferSize()));return created;
            });
            return super.getBuffer(layer);
        }
        @Override public void endBatch() {
            endLastBatch();
            for(var layer:fixedBuffers.keySet())if(!((ViewLayer)layer).foil)super.endBatch(layer);
            for(var layer:fixedBuffers.keySet())if(((ViewLayer)layer).foil)super.endBatch(layer);
        }
        @Override public void close(){for(var buffer:fixedBuffers.values())buffer.close();sharedBuffer.close();fixedBuffers.clear();layers.clear();}
    }
    /** Preserve original material/cutout/sorting while directing its output into the secondary view. */
    private static final class ViewLayer extends RenderType {
        private final boolean foil;
        ViewLayer(RenderType original) {
            super("stillworld_connection_"+original,original.format(),original.mode(),original.bufferSize(),
                    original.affectsCrumbling(),original.sortOnUpload(),
                    ()->{original.setupRenderState();remote.bindWrite(true);},
                    ()->{original.clearRenderState();remote.bindWrite(true);});
            foil=original==RenderType.glint() || original==RenderType.glintTranslucent() || original==RenderType.armorEntityGlint()
                    || original==RenderType.entityGlint() || original==RenderType.entityGlintDirect();
        }
    }
    private static final class ViewCamera extends Camera {
        ViewCamera(Camera original,Vec3 position,float partial) {
            var mc=Minecraft.getInstance();setup(mc.level,original.getEntity(),original.isDetached(),false,partial);
            setPosition(position);setRotation(original.getYRot()-90,original.getXRot(),original.getRoll());
        }
    }
    private static void composite(RenderLevelStageEvent event,Vec3 camera,float strength) {
        compositor.setSampler("SceneSampler",source.getColorTextureId());
        compositor.setSampler("DepthSampler",source.getDepthTextureId());
        compositor.setSampler("ConnectionSampler",remote.getColorTextureId());
        compositor.safeGetUniform("InverseViewProjection").set(new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix()).invert());
        uniform("PlaneCenter",connection.center().subtract(camera));uniform("PlaneNormal",connection.forward());uniform("PlaneRight",connection.right());
        compositor.safeGetUniform("HalfSize").set(SpatialConnection.HALF_WIDTH,SpatialConnection.HALF_HEIGHT);
        compositor.safeGetUniform("Strength").set(strength);
        RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableBlend();RenderSystem.setShader(()->compositor);
        var quad=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION);
        quad.addVertex(-1,-1,0);quad.addVertex(1,-1,0);quad.addVertex(1,1,0);quad.addVertex(-1,1,0);
        BufferUploader.drawWithShader(quad.buildOrThrow());
    }
    private static void uniform(String name,Vec3 vector){compositor.safeGetUniform(name).set((float)vector.x,(float)vector.y,(float)vector.z);}
    private static void copy(RenderTarget from,RenderTarget to) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,from.frameBufferId);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,to.frameBufferId);
        GL30.glBlitFramebuffer(0,0,from.width,from.height,0,0,to.width,to.height,GL30.GL_COLOR_BUFFER_BIT|GL30.GL_DEPTH_BUFFER_BIT,GL30.GL_NEAREST);
        to.bindWrite(true);
    }
    private static void resize(int width,int height)throws IOException {
        if(remote!=null && remote.width==width && remote.height==height)return;
        releaseTargets();var mc=Minecraft.getInstance();
        source=new TextureTarget(width,height,true,Minecraft.ON_OSX);remote=new TextureTarget(width,height,true,Minecraft.ON_OSX);
        remoteDepth=new TextureTarget(width,height,true,Minecraft.ON_OSX);
        buffers=new ViewBuffers();indices=new ByteBufferBuilder(4096);elementBuffer=GL30.glGenBuffers();
        colorChain=new PostChain(mc.getTextureManager(),mc.getResourceManager(),remote,Stillworld.id("shaders/post/monochrome.json"));
        colorChain.resize(width,height);
    }
    private static void releaseTargets() {
        if(colorChain!=null){colorChain.close();colorChain=null;}
        for(var target:new RenderTarget[]{source,remote,remoteDepth})if(target!=null)target.destroyBuffers();
        source=remote=remoteDepth=null;
        if(buffers!=null){buffers.close();buffers=null;}
        if(indices!=null){indices.close();indices=null;}
        if(elementBuffer!=0){GL30.glDeleteBuffers(elementBuffer);elementBuffer=0;}
    }
    static void reload(){releaseTargets();failed=false;renderedViews=0;}
    static void clear(){reload();connection=null;}
    private ConnectionRenderer(){ }
}
